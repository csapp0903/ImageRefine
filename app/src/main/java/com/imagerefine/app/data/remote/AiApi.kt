package com.imagerefine.app.data.remote

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * AI 后端 API 接口
 *
 * 端点说明：
 * - /api/enhance         : 智能美化，分析图片返回推荐参数
 * - /api/style-transfer  : 风格迁移，将风格图的风格应用到内容图
 * - /api/segment         : 智能抠图，返回前景 mask
 * - /api/beauty          : 人像美颜，返回处理后图片
 */
interface AiApi {

    /** 智能美化：上传图片，返回增强后图片 */
    @Multipart
    @POST("api/enhance")
    suspend fun autoEnhance(
        @Part image: MultipartBody.Part
    ): ResponseBody

    /** 风格迁移：上传内容图和风格图，返回迁移后图片 */
    @Multipart
    @POST("api/style-transfer")
    suspend fun styleTransfer(
        @Part content: MultipartBody.Part,
        @Part style: MultipartBody.Part
    ): ResponseBody

    /** 智能抠图：上传图片，返回去除背景后的前景 PNG */
    @Multipart
    @POST("api/segment")
    suspend fun removeBackground(
        @Part image: MultipartBody.Part
    ): ResponseBody

    /** 人像美颜：上传人像照片及美颜参数，返回处理后图片 */
    @Multipart
    @POST("api/beauty")
    suspend fun beautyFace(
        @Part image: MultipartBody.Part,
        @Part("smooth") smooth: Float,
        @Part("whiten") whiten: Float,
        @Part("thin_face") thinFace: Float,
        @Part("big_eye") bigEye: Float
    ): ResponseBody
}
