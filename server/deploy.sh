#!/bin/bash
# ============================================================
# ImageRefine 后端服务 - 京东云服务器一键部署脚本
# 
# 使用方式:
#   1. 将 server/ 目录上传到京东云服务器
#   2. chmod +x deploy.sh
#   3. ./deploy.sh
#
# 前置条件: Python 3.9+, pip
# ============================================================

set -e

echo "=========================================="
echo "  ImageRefine 后端部署脚本"
echo "=========================================="

# 检查 Python 版本
PYTHON_CMD=""
if command -v python3 &> /dev/null; then
    PYTHON_CMD="python3"
elif command -v python &> /dev/null; then
    PYTHON_CMD="python"
else
    echo "❌ 未找到 Python，请先安装 Python 3.9+"
    exit 1
fi

PY_VERSION=$($PYTHON_CMD --version 2>&1 | awk '{print $2}')
echo "✅ Python 版本: $PY_VERSION"

# 创建虚拟环境
if [ ! -d "venv" ]; then
    echo "📦 创建虚拟环境..."
    $PYTHON_CMD -m venv venv
fi

source venv/bin/activate
echo "✅ 虚拟环境已激活"

# 安装依赖
echo "📦 安装依赖..."
pip install --upgrade pip -q
pip install -r requirements.txt -q
echo "✅ 依赖安装完成"

# 检查 .env 配置
if [ ! -f ".env" ]; then
    echo ""
    echo "⚠️  未找到 .env 配置文件"
    echo "   正在从 .env.example 复制..."
    cp .env.example .env
    echo ""
    echo "🔧 请编辑 .env 文件，填入阿里云凭证:"
    echo "   vi .env"
    echo ""
    echo "   必填项:"
    echo "   - ALIBABA_ACCESS_KEY_ID"
    echo "   - ALIBABA_ACCESS_KEY_SECRET"
    echo "   - OSS_BUCKET_NAME"
    echo ""
    echo "   填好后重新运行: ./deploy.sh"
    exit 0
fi

# 验证配置
echo "🔍 验证配置..."
$PYTHON_CMD -c "
from dotenv import load_dotenv
load_dotenv()
from config import Config
cfg = Config.from_env()
cfg.validate()
print(f'  区域: {cfg.region}')
print(f'  OSS: {cfg.oss_bucket}')
print(f'  端口: {cfg.port}')
print(f'  限流: {cfg.rate_limit}/min')
print(f'  鉴权: {\"已启用\" if cfg.api_token else \"未启用\"}')
"
echo "✅ 配置验证通过"

# 检查端口是否被占用
PORT=$(grep SERVER_PORT .env 2>/dev/null | cut -d= -f2 || echo "8000")
PORT=${PORT:-8000}
if ss -tlnp | grep -q ":$PORT " 2>/dev/null || lsof -i :$PORT &>/dev/null 2>&1; then
    echo "⚠️  端口 $PORT 已被占用，尝试关闭旧进程..."
    pkill -f "uvicorn main:app" 2>/dev/null || true
    sleep 2
fi

# 启动服务
echo ""
echo "🚀 启动服务 (端口: $PORT)..."
echo ""

# 使用 nohup 后台运行
nohup $PYTHON_CMD -m uvicorn main:app \
    --host 0.0.0.0 \
    --port $PORT \
    --workers 2 \
    --log-level info \
    > server.log 2>&1 &

SERVER_PID=$!
echo $SERVER_PID > server.pid
sleep 2

# 检查是否启动成功
if kill -0 $SERVER_PID 2>/dev/null; then
    echo "✅ 服务启动成功!"
    echo ""
    echo "=========================================="
    echo "  服务信息:"
    echo "  PID:     $SERVER_PID"
    echo "  端口:    $PORT"
    echo "  日志:    tail -f server.log"
    echo "  停止:    ./stop.sh 或 kill $SERVER_PID"
    echo ""
    echo "  健康检查:"
    echo "  curl http://localhost:$PORT/api/health"
    echo ""
    echo "  App 端配置服务器地址为:"
    echo "  http://<你的京东云公网IP>:$PORT/"
    echo "=========================================="
else
    echo "❌ 服务启动失败，查看日志:"
    cat server.log
    exit 1
fi
