"""
ImageRefine 后端代理服务器 - 阿里云 VIAPI 客户端封装

封装所有阿里云视觉智能 API 的调用逻辑。
服务器本身不做任何 AI 推理，仅作为代理转发。
"""
import httpx
from alibabacloud_tea_openapi import models as open_api_models

from alibabacloud_imageenhan20190930.client import Client as ImageEnhanClient
from alibabacloud_imageenhan20190930 import models as imageenhan_models

from alibabacloud_imageseg20191230.client import Client as ImageSegClient
from alibabacloud_imageseg20191230 import models as imageseg_models

from alibabacloud_facebody20191230.client import Client as FaceBodyClient
from alibabacloud_facebody20191230 import models as facebody_models

from config import Config
from oss_helper import OssHelper


class AliyunViapiClient:
    """阿里云视觉智能 API 代理客户端"""

    def __init__(self, cfg: Config, oss: OssHelper):
        self.oss = oss
        self._cfg = cfg

        self.enhan_client = ImageEnhanClient(self._make_config("imageenhan"))
        self.seg_client = ImageSegClient(self._make_config("imageseg"))
        self.face_client = FaceBodyClient(self._make_config("facebody"))

    def _make_config(self, product: str) -> open_api_models.Config:
        return open_api_models.Config(
            access_key_id=self._cfg.access_key_id,
            access_key_secret=self._cfg.access_key_secret,
            endpoint=f"{product}.{self._cfg.region}.aliyuncs.com",
        )

    async def enhance(self, image_bytes: bytes) -> bytes:
        """
        图像增强（智能美化）
        输入: 原始图片字节
        输出: 增强后图片字节
        """
        image_url = self.oss.upload(image_bytes)
        request = imageenhan_models.ImageBlindPicQualityEnhanceRequest(
            image_url=image_url,
        )
        response = self.enhan_client.image_blind_pic_quality_enhance(request)
        result_url = response.body.data.image_url

        async with httpx.AsyncClient(timeout=120) as client:
            resp = await client.get(result_url)
            resp.raise_for_status()
            return resp.content

    async def style_transfer(
        self, content_bytes: bytes, style_bytes: bytes
    ) -> bytes:
        """
        风格迁移
        输入: 内容图字节 + 风格图字节
        输出: 迁移后图片字节
        """
        content_url = self.oss.upload(content_bytes, ".jpg")
        style_url = self.oss.upload(style_bytes, "_style.jpg")

        request = imageenhan_models.ImageBlindStyleTransferRequest(
            image_url=content_url,
            style_url=style_url,
        )
        response = self.enhan_client.image_blind_style_transfer(request)
        result_url = response.body.data.image_url

        async with httpx.AsyncClient(timeout=120) as client:
            resp = await client.get(result_url)
            resp.raise_for_status()
            return resp.content

    async def segment(self, image_bytes: bytes) -> bytes:
        """
        人像 / 通用抠图
        输入: 原始图片字节
        输出: 透明背景前景 PNG 字节
        """
        image_url = self.oss.upload(image_bytes)

        # 优先尝试人体分割
        try:
            request = imageseg_models.SegmentBodyRequest(image_url=image_url)
            response = self.seg_client.segment_body(request)
            result_url = response.body.data.image_url
        except Exception:
            # 人体分割失败时回退到通用分割
            request = imageseg_models.SegmentCommonImageRequest(image_url=image_url)
            response = self.seg_client.segment_common_image(request)
            result_url = response.body.data.image_url

        async with httpx.AsyncClient(timeout=120) as client:
            resp = await client.get(result_url)
            resp.raise_for_status()
            return resp.content

    async def beauty(
        self,
        image_bytes: bytes,
        smooth: float = 0.5,
        whiten: float = 0.3,
        sharp: float = 0.5,
    ) -> bytes:
        """
        人脸美颜
        输入: 人像图片字节 + 美颜参数 (0-1)
        输出: 美颜后图片字节
        """
        image_url = self.oss.upload(image_bytes)

        request = facebody_models.FaceBeautyRequest(
            image_url=image_url,
            smooth=smooth,
            white=whiten,
            sharp=sharp,
        )
        response = self.face_client.face_beauty(request)
        result_url = response.body.data.image_url

        async with httpx.AsyncClient(timeout=120) as client:
            resp = await client.get(result_url)
            resp.raise_for_status()
            return resp.content
