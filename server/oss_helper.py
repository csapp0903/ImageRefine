"""
ImageRefine 后端代理服务器 - OSS 工具

负责将图片上传到 OSS 并生成临时签名 URL，
供阿里云 VIAPI 读取。
"""
import uuid

import oss2

from config import Config


class OssHelper:
    def __init__(self, cfg: Config):
        auth = oss2.Auth(cfg.access_key_id, cfg.access_key_secret)
        self.bucket = oss2.Bucket(auth, cfg.oss_endpoint, cfg.oss_bucket)
        self.url_expiry = cfg.oss_url_expiry

    def upload(self, file_bytes: bytes, suffix: str = ".jpg") -> str:
        """上传图片到 OSS，返回带签名的临时访问 URL"""
        key = f"imagerefine/tmp/{uuid.uuid4().hex}{suffix}"
        self.bucket.put_object(key, file_bytes)
        url = self.bucket.sign_url("GET", key, self.url_expiry)
        return url

    def cleanup(self, key: str):
        """删除 OSS 上的临时文件（可选调用）"""
        try:
            self.bucket.delete_object(key)
        except Exception:
            pass
