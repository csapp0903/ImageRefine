package com.imagerefine.app.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import com.imagerefine.app.data.model.ImageParameters

/**
 * 图像处理引擎 - 使用 ColorMatrix 实现参数调整
 */
object ImageProcessor {

    /**
     * 应用所有参数到图像
     */
    fun applyParameters(source: Bitmap, params: ImageParameters): Bitmap {
        if (params.isDefault()) return source.copy(Bitmap.Config.ARGB_8888, false)

        val width = source.width
        val height = source.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 组合 ColorMatrix
        val combinedMatrix = ColorMatrix()

        // 亮度
        if (params.brightness != 0f) {
            val brightnessMatrix = createBrightnessMatrix(params.brightness)
            combinedMatrix.postConcat(brightnessMatrix)
        }

        // 对比度
        if (params.contrast != 0f) {
            val contrastMatrix = createContrastMatrix(params.contrast)
            combinedMatrix.postConcat(contrastMatrix)
        }

        // 饱和度
        if (params.saturation != 0f) {
            val saturationMatrix = createSaturationMatrix(params.saturation)
            combinedMatrix.postConcat(saturationMatrix)
        }

        // 色温
        if (params.warmth != 0f) {
            val warmthMatrix = createWarmthMatrix(params.warmth)
            combinedMatrix.postConcat(warmthMatrix)
        }

        // 曝光
        if (params.exposure != 0f) {
            val exposureMatrix = createExposureMatrix(params.exposure)
            combinedMatrix.postConcat(exposureMatrix)
        }

        // 高光
        if (params.highlights != 0f) {
            val highlightsMatrix = createHighlightsMatrix(params.highlights)
            combinedMatrix.postConcat(highlightsMatrix)
        }

        // 阴影
        if (params.shadows != 0f) {
            val shadowsMatrix = createShadowsMatrix(params.shadows)
            combinedMatrix.postConcat(shadowsMatrix)
        }

        // 色调
        if (params.tint != 0f) {
            val tintMatrix = createTintMatrix(params.tint)
            combinedMatrix.postConcat(tintMatrix)
        }

        paint.colorFilter = ColorMatrixColorFilter(combinedMatrix)
        canvas.drawBitmap(source, 0f, 0f, paint)

        // 锐化
        if (params.sharpness != 0f) {
            applySharpen(result, params.sharpness)
        }

        // 暗角
        if (params.vignette > 0f) {
            applyVignette(result, canvas, params.vignette)
        }

        return result
    }

    /** 亮度矩阵: 加减 RGB 通道值 */
    private fun createBrightnessMatrix(value: Float): ColorMatrix {
        val v = value * 2.55f // 映射到 0-255 范围
        return ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, v,
            0f, 1f, 0f, 0f, v,
            0f, 0f, 1f, 0f, v,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    /** 对比度矩阵 */
    private fun createContrastMatrix(value: Float): ColorMatrix {
        val factor = (100f + value) / 100f
        val scale = factor * factor
        val translate = 128f * (1f - scale)
        return ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    /** 饱和度矩阵 */
    private fun createSaturationMatrix(value: Float): ColorMatrix {
        val matrix = ColorMatrix()
        val sat = 1f + value / 100f
        matrix.setSaturation(sat.coerceAtLeast(0f))
        return matrix
    }

    /** 色温矩阵: 暖色增加R减少B，冷色反之 */
    private fun createWarmthMatrix(value: Float): ColorMatrix {
        val warmth = value / 100f * 30f
        return ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, warmth,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, -warmth,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    /** 曝光矩阵 */
    private fun createExposureMatrix(value: Float): ColorMatrix {
        val factor = 1f + value / 100f
        return ColorMatrix(floatArrayOf(
            factor, 0f, 0f, 0f, 0f,
            0f, factor, 0f, 0f, 0f,
            0f, 0f, factor, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    /** 高光矩阵 */
    private fun createHighlightsMatrix(value: Float): ColorMatrix {
        val v = value / 100f * 20f
        return ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, v,
            0f, 1f, 0f, 0f, v,
            0f, 0f, 1f, 0f, v,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    /** 阴影矩阵 */
    private fun createShadowsMatrix(value: Float): ColorMatrix {
        val factor = 1f + value / 200f
        val translate = value / 100f * 10f
        return ColorMatrix(floatArrayOf(
            factor, 0f, 0f, 0f, translate,
            0f, factor, 0f, 0f, translate,
            0f, 0f, factor, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    /** 色调矩阵: 在 green-magenta 轴偏移 */
    private fun createTintMatrix(value: Float): ColorMatrix {
        val tint = value / 100f * 20f
        return ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, tint,
            0f, 1f, 0f, 0f, -tint,
            0f, 0f, 1f, 0f, tint,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    /** 简单USM锐化 */
    private fun applySharpen(bitmap: Bitmap, amount: Float) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val original = pixels.copyOf()
        val strength = amount / 100f * 0.5f

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                val center = original[idx]
                val top = original[(y - 1) * width + x]
                val bottom = original[(y + 1) * width + x]
                val left = original[y * width + (x - 1)]
                val right = original[y * width + (x + 1)]

                val avgR = ((top shr 16 and 0xFF) + (bottom shr 16 and 0xFF) +
                        (left shr 16 and 0xFF) + (right shr 16 and 0xFF)) / 4
                val avgG = ((top shr 8 and 0xFF) + (bottom shr 8 and 0xFF) +
                        (left shr 8 and 0xFF) + (right shr 8 and 0xFF)) / 4
                val avgB = ((top and 0xFF) + (bottom and 0xFF) +
                        (left and 0xFF) + (right and 0xFF)) / 4

                val cR = (center shr 16 and 0xFF)
                val cG = (center shr 8 and 0xFF)
                val cB = (center and 0xFF)

                val r = (cR + ((cR - avgR) * strength).toInt()).coerceIn(0, 255)
                val g = (cG + ((cG - avgG) * strength).toInt()).coerceIn(0, 255)
                val b = (cB + ((cB - avgB) * strength).toInt()).coerceIn(0, 255)

                pixels[idx] = (center and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    /** 暗角效果 */
    private fun applyVignette(bitmap: Bitmap, canvas: Canvas, amount: Float) {
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = Math.sqrt((centerX * centerX + centerY * centerY).toDouble()).toFloat()

        val alpha = (amount / 100f * 200f).toInt().coerceIn(0, 200)

        val gradient = RadialGradient(
            centerX, centerY, radius,
            intArrayOf(0x00000000, 0x00000000, (alpha shl 24)),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )

        val paint = Paint()
        paint.shader = gradient
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        canvas.drawRect(0f, 0f, width, height, paint)
    }
}
