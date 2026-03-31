package com.imagerefine.app.data.remote.model

import com.google.gson.annotations.SerializedName

/**
 * AI 智能美化响应 - 返回推荐的参数
 */
data class EnhanceResponse(
    @SerializedName("brightness") val brightness: Float = 0f,
    @SerializedName("contrast") val contrast: Float = 0f,
    @SerializedName("saturation") val saturation: Float = 0f,
    @SerializedName("warmth") val warmth: Float = 0f,
    @SerializedName("exposure") val exposure: Float = 0f,
    @SerializedName("highlights") val highlights: Float = 0f,
    @SerializedName("shadows") val shadows: Float = 0f,
    @SerializedName("sharpness") val sharpness: Float = 0f,
    @SerializedName("vignette") val vignette: Float = 0f,
    @SerializedName("tint") val tint: Float = 0f,
    @SerializedName("scene") val scene: String = "",       // 识别的场景类型
    @SerializedName("confidence") val confidence: Float = 0f
)

/**
 * AI 抠图响应
 */
data class SegmentResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String = ""
    // mask 图片通过 ResponseBody 返回二进制数据
)

/**
 * AI 人像美颜请求参数
 */
data class BeautyOptions(
    @SerializedName("smooth") val smooth: Float = 50f,      // 磨皮 0-100
    @SerializedName("whiten") val whiten: Float = 30f,      // 美白 0-100
    @SerializedName("thin_face") val thinFace: Float = 20f, // 瘦脸 0-100
    @SerializedName("big_eye") val bigEye: Float = 15f      // 大眼 0-100
)

/**
 * 通用 AI 状态
 */
enum class AiTaskStatus {
    IDLE,
    LOADING,
    SUCCESS,
    ERROR
}

/**
 * AI 任务结果封装
 */
data class AiResult<T>(
    val status: AiTaskStatus = AiTaskStatus.IDLE,
    val data: T? = null,
    val error: String? = null
) {
    companion object {
        fun <T> idle() = AiResult<T>(AiTaskStatus.IDLE)
        fun <T> loading() = AiResult<T>(AiTaskStatus.LOADING)
        fun <T> success(data: T) = AiResult<T>(AiTaskStatus.SUCCESS, data = data)
        fun <T> error(message: String) = AiResult<T>(AiTaskStatus.ERROR, error = message)
    }
}
