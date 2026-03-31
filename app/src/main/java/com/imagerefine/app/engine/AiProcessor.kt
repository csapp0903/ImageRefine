package com.imagerefine.app.engine

import android.graphics.Bitmap
import com.imagerefine.app.data.remote.AiService
import com.imagerefine.app.data.remote.model.BeautyOptions

/**
 * AI 处理引擎 - 对接 AiService，提供高级图像处理能力
 *
 * 本地 ImageProcessor 与云端 AiProcessor 配合使用：
 * - 本地：参数调节、滤镜、基本风格提取（离线可用）
 * - 云端：智能美化、风格迁移、抠图、美颜（需网络）
 */
class AiProcessor(private val aiService: AiService) {

    /**
     * AI 智能美化 - 上传图片到云端增强，返回增强后的 Bitmap
     */
    suspend fun smartEnhance(bitmap: Bitmap): Result<Bitmap> {
        return aiService.autoEnhance(bitmap)
    }

    /**
     * AI 风格迁移 - 将参考图的风格应用到目标图片
     * 返回处理后的完整 Bitmap（不经过本地 ColorMatrix）
     */
    suspend fun styleTransfer(content: Bitmap, style: Bitmap): Result<Bitmap> {
        return aiService.styleTransfer(content, style)
    }

    /**
     * AI 智能抠图 - 移除背景，返回透明背景的前景图
     */
    suspend fun removeBackground(bitmap: Bitmap): Result<Bitmap> {
        return aiService.removeBackground(bitmap)
    }

    /**
     * AI 人像美颜
     * @param bitmap 原始人像图片
     * @param options 美颜参数（磨皮、美白、瘦脸、大眼）
     */
    suspend fun beautyFace(bitmap: Bitmap, options: BeautyOptions): Result<Bitmap> {
        return aiService.beautyFace(bitmap, options)
    }

    /**
     * 合成抠图结果与新背景
     * @param foreground 抠出的前景（透明背景 PNG）
     * @param background 新背景图片
     */
    fun compositeWithBackground(foreground: Bitmap, background: Bitmap): Bitmap {
        val width = foreground.width
        val height = foreground.height
        val scaledBg = Bitmap.createScaledBitmap(background, width, height, true)
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        canvas.drawBitmap(scaledBg, 0f, 0f, null)
        canvas.drawBitmap(foreground, 0f, 0f, null)
        return result
    }
}
