#!/bin/bash
# 停止 ImageRefine 后端服务

if [ -f "server.pid" ]; then
    PID=$(cat server.pid)
    if kill -0 $PID 2>/dev/null; then
        kill $PID
        echo "✅ 服务已停止 (PID: $PID)"
    else
        echo "⚠️  进程 $PID 不存在"
    fi
    rm -f server.pid
else
    # 尝试按进程名查找
    pkill -f "uvicorn main:app" 2>/dev/null
    if [ $? -eq 0 ]; then
        echo "✅ 服务已停止"
    else
        echo "⚠️  未找到运行中的服务"
    fi
fi
