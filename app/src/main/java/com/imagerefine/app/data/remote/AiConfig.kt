package com.imagerefine.app.data.remote

/**
 * AI 服务配置
 * 实际使用时需要将 BASE_URL 替换为你的后端服务器地址
 */
object AiConfig {
    /** 后端服务器地址，末尾需要带 / */
    var BASE_URL = "http://10.0.2.2:8000/"  // 模拟器访问宿主机

    /** 请求超时时间（秒） */
    const val CONNECT_TIMEOUT = 30L
    const val READ_TIMEOUT = 120L
    const val WRITE_TIMEOUT = 60L

    /** 上传图片最大尺寸（像素） */
    const val MAX_UPLOAD_SIZE = 1920

    /** 上传图片压缩质量 */
    const val UPLOAD_QUALITY = 85
}
