package com.imagerefine.app.data.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.imagerefine.app.data.remote.model.BeautyOptions
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * AI 服务封装层
 * 处理图片编解码、网络请求、错误处理
 */
class AiService private constructor() {

    private val api: AiApi

    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(AiConfig.CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(AiConfig.READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(AiConfig.WRITE_TIMEOUT, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(AiConfig.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(AiApi::class.java)
    }

    /**
     * AI 智能美化 - 上传图片，返回增强后的 Bitmap
     */
    suspend fun autoEnhance(bitmap: Bitmap): Result<Bitmap> {
        return try {
            val part = bitmapToMultipart(bitmap, "image")
            val response = api.autoEnhance(part)
            val bytes = response.bytes()
            val result = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return Result.failure(Exception("无法解码返回的图片"))
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * AI 风格迁移 - 将风格图的风格迁移到内容图
     */
    suspend fun styleTransfer(content: Bitmap, style: Bitmap): Result<Bitmap> {
        return try {
            val contentPart = bitmapToMultipart(content, "content")
            val stylePart = bitmapToMultipart(style, "style")
            val response = api.styleTransfer(contentPart, stylePart)
            val bytes = response.bytes()
            val result = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return Result.failure(Exception("无法解码返回的图片"))
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * AI 智能抠图 - 去除背景
     */
    suspend fun removeBackground(bitmap: Bitmap): Result<Bitmap> {
        return try {
            val part = bitmapToMultipart(bitmap, "image")
            val response = api.removeBackground(part)
            val bytes = response.bytes()
            val result = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return Result.failure(Exception("无法解码返回的图片"))
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * AI 人像美颜
     */
    suspend fun beautyFace(bitmap: Bitmap, options: BeautyOptions = BeautyOptions()): Result<Bitmap> {
        return try {
            val part = bitmapToMultipart(bitmap, "image")
            val response = api.beautyFace(
                image = part,
                smooth = options.smooth,
                whiten = options.whiten,
                thinFace = options.thinFace,
                bigEye = options.bigEye
            )
            val bytes = response.bytes()
            val result = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return Result.failure(Exception("无法解码返回的图片"))
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 将 Bitmap 压缩为 MultipartBody.Part
     */
    private fun bitmapToMultipart(bitmap: Bitmap, partName: String): MultipartBody.Part {
        // 如果图片太大，先缩放
        val scaledBitmap = scaleBitmapIfNeeded(bitmap)

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, AiConfig.UPLOAD_QUALITY, outputStream)
        val bytes = outputStream.toByteArray()

        val requestBody = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(partName, "$partName.jpg", requestBody)
    }

    /**
     * 限制上传图片尺寸
     */
    private fun scaleBitmapIfNeeded(bitmap: Bitmap): Bitmap {
        val maxSize = AiConfig.MAX_UPLOAD_SIZE
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxSize && height <= maxSize) return bitmap

        val scale = maxSize.toFloat() / maxOf(width, height)
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    companion object {
        @Volatile
        private var instance: AiService? = null

        fun getInstance(): AiService {
            return instance ?: synchronized(this) {
                instance ?: AiService().also { instance = it }
            }
        }

        /** 更新服务器地址后需要重建实例 */
        fun resetInstance() {
            synchronized(this) {
                instance = null
            }
        }
    }
}
