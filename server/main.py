"""
ImageRefine 后端代理服务器

架构: App → (HTTPS) → 京东云服务器(本服务) → 阿里云 VIAPI
服务器本身不做 AI 推理，仅做请求转发和图片中转。

端点:
  POST /api/enhance        - 智能美化（返回增强后图片）
  POST /api/style-transfer - 风格迁移（返回迁移后图片）
  POST /api/segment        - 智能抠图（返回透明背景 PNG）
  POST /api/beauty         - 人像美颜（返回美颜后图片）
  GET  /api/health         - 健康检查
"""
import logging
import time
from collections import defaultdict
from contextlib import asynccontextmanager

from dotenv import load_dotenv
from fastapi import FastAPI, File, Form, HTTPException, Request, UploadFile
from fastapi.responses import Response

from aliyun_client import AliyunViapiClient
from config import Config
from oss_helper import OssHelper

# 加载 .env
load_dotenv()

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
logger = logging.getLogger("imagerefine")

# ===================== 全局对象 =====================

cfg: Config
oss: OssHelper
viapi: AliyunViapiClient

# 简易限流：IP → [timestamp, ...]
_rate_limits: dict[str, list[float]] = defaultdict(list)


@asynccontextmanager
async def lifespan(app: FastAPI):
    global cfg, oss, viapi
    cfg = Config.from_env()
    cfg.validate()
    oss = OssHelper(cfg)
    viapi = AliyunViapiClient(cfg, oss)
    logger.info("服务启动完成 — 区域: %s, OSS: %s", cfg.region, cfg.oss_bucket)
    yield
    logger.info("服务已关闭")


app = FastAPI(
    title="ImageRefine AI Proxy",
    version="1.0.0",
    lifespan=lifespan,
)


# ===================== 中间件 =====================


@app.middleware("http")
async def auth_and_rate_limit(request: Request, call_next):
    # Token 鉴权（如果配置了）
    if cfg.api_token:
        token = request.headers.get("Authorization", "").removeprefix("Bearer ").strip()
        if token != cfg.api_token:
            return Response(content='{"detail":"未授权"}', status_code=401,
                            media_type="application/json")

    # 简易 IP 限流
    client_ip = request.client.host if request.client else "unknown"
    now = time.time()
    window = _rate_limits[client_ip]
    # 清理 60 秒前的记录
    _rate_limits[client_ip] = [t for t in window if now - t < 60]
    if len(_rate_limits[client_ip]) >= cfg.rate_limit:
        return Response(content='{"detail":"请求过于频繁，请稍后再试"}', status_code=429,
                        media_type="application/json")
    _rate_limits[client_ip].append(now)

    return await call_next(request)


# ===================== 端点 =====================


@app.get("/api/health")
async def health_check():
    return {"status": "ok", "region": cfg.region}


@app.post("/api/enhance")
async def enhance(image: UploadFile = File(...)):
    """
    智能图片增强(美化)
    - 输入: multipart/form-data, field name = image
    - 输出: 增强后的 JPEG 图片二进制
    """
    logger.info("收到智能美化请求: %s (%s)", image.filename, image.content_type)
    try:
        img_bytes = await image.read()
        _check_file_size(img_bytes)
        result = await viapi.enhance(img_bytes)
        return Response(content=result, media_type="image/jpeg")
    except HTTPException:
        raise
    except Exception as e:
        logger.exception("智能美化失败")
        raise HTTPException(status_code=500, detail=f"智能美化失败: {e}")


@app.post("/api/style-transfer")
async def style_transfer(
    content: UploadFile = File(...),
    style: UploadFile = File(...),
):
    """
    风格迁移
    - 输入: multipart/form-data, fields = content + style
    - 输出: 迁移后的 JPEG 图片二进制
    """
    logger.info("收到风格迁移请求: content=%s, style=%s", content.filename, style.filename)
    try:
        content_bytes = await content.read()
        style_bytes = await style.read()
        _check_file_size(content_bytes)
        _check_file_size(style_bytes)
        result = await viapi.style_transfer(content_bytes, style_bytes)
        return Response(content=result, media_type="image/jpeg")
    except HTTPException:
        raise
    except Exception as e:
        logger.exception("风格迁移失败")
        raise HTTPException(status_code=500, detail=f"风格迁移失败: {e}")


@app.post("/api/segment")
async def segment(image: UploadFile = File(...)):
    """
    智能抠图
    - 输入: multipart/form-data, field name = image
    - 输出: 透明背景的 PNG 图片二进制
    """
    logger.info("收到抠图请求: %s", image.filename)
    try:
        img_bytes = await image.read()
        _check_file_size(img_bytes)
        result = await viapi.segment(img_bytes)
        return Response(content=result, media_type="image/png")
    except HTTPException:
        raise
    except Exception as e:
        logger.exception("抠图失败")
        raise HTTPException(status_code=500, detail=f"抠图失败: {e}")


@app.post("/api/beauty")
async def beauty(
    image: UploadFile = File(...),
    smooth: float = Form(50.0),
    whiten: float = Form(30.0),
    thin_face: float = Form(20.0),
    big_eye: float = Form(15.0),
):
    """
    人像美颜
    - 输入: multipart/form-data, fields = image + smooth/whiten/thin_face/big_eye (0-100)
    - 输出: 美颜后的 JPEG 图片二进制
    - 注: thin_face 和 big_eye 参数由后端留存，阿里云 FaceBeauty 仅支持 smooth/white/sharp
    """
    logger.info("收到美颜请求: smooth=%.1f, whiten=%.1f", smooth, whiten)
    try:
        img_bytes = await image.read()
        _check_file_size(img_bytes)
        result = await viapi.beauty(
            img_bytes,
            smooth=smooth / 100.0,
            whiten=whiten / 100.0,
            sharp=0.5,
        )
        return Response(content=result, media_type="image/jpeg")
    except HTTPException:
        raise
    except Exception as e:
        logger.exception("美颜失败")
        raise HTTPException(status_code=500, detail=f"美颜失败: {e}")


# ===================== 工具 =====================


_MAX_UPLOAD_BYTES = 10 * 1024 * 1024  # 10 MB


def _check_file_size(data: bytes):
    if len(data) > _MAX_UPLOAD_BYTES:
        raise HTTPException(
            status_code=413,
            detail=f"图片过大，最大允许 {_MAX_UPLOAD_BYTES // 1024 // 1024} MB",
        )


# ===================== 入口 =====================

if __name__ == "__main__":
    import uvicorn

    load_dotenv()
    _cfg = Config.from_env()
    uvicorn.run("main:app", host=_cfg.host, port=_cfg.port, reload=False)
