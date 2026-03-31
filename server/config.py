"""
ImageRefine 后端代理服务器 - 配置管理

从 .env 文件或环境变量读取配置
"""
import os
from dataclasses import dataclass


@dataclass
class Config:
    # 阿里云凭证
    access_key_id: str = ""
    access_key_secret: str = ""
    region: str = "cn-shanghai"

    # OSS
    oss_bucket: str = ""
    oss_endpoint: str = "oss-cn-shanghai.aliyuncs.com"
    oss_url_expiry: int = 3600

    # 服务器
    host: str = "0.0.0.0"
    port: int = 8000

    # 限流
    rate_limit: int = 30

    # 鉴权
    api_token: str = ""

    @classmethod
    def from_env(cls) -> "Config":
        return cls(
            access_key_id=os.getenv("ALIBABA_ACCESS_KEY_ID", ""),
            access_key_secret=os.getenv("ALIBABA_ACCESS_KEY_SECRET", ""),
            region=os.getenv("ALIBABA_REGION", "cn-shanghai"),
            oss_bucket=os.getenv("OSS_BUCKET_NAME", ""),
            oss_endpoint=os.getenv("OSS_ENDPOINT", "oss-cn-shanghai.aliyuncs.com"),
            oss_url_expiry=int(os.getenv("OSS_URL_EXPIRY", "3600")),
            host=os.getenv("SERVER_HOST", "0.0.0.0"),
            port=int(os.getenv("SERVER_PORT", "8000")),
            rate_limit=int(os.getenv("RATE_LIMIT_PER_MINUTE", "30")),
            api_token=os.getenv("API_TOKEN", ""),
        )

    def validate(self):
        errors = []
        if not self.access_key_id:
            errors.append("ALIBABA_ACCESS_KEY_ID 未设置")
        if not self.access_key_secret:
            errors.append("ALIBABA_ACCESS_KEY_SECRET 未设置")
        if not self.oss_bucket:
            errors.append("OSS_BUCKET_NAME 未设置")
        if errors:
            raise ValueError("配置错误:\n" + "\n".join(f"  - {e}" for e in errors))
