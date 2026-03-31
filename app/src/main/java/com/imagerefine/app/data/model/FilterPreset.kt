package com.imagerefine.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 图像编辑参数数据类
 */
data class ImageParameters(
    val brightness: Float = 0f,      // -100 ~ 100
    val contrast: Float = 0f,        // -100 ~ 100
    val saturation: Float = 0f,      // -100 ~ 100
    val warmth: Float = 0f,          // -100 ~ 100
    val exposure: Float = 0f,        // -100 ~ 100
    val highlights: Float = 0f,      // -100 ~ 100
    val shadows: Float = 0f,         // -100 ~ 100
    val sharpness: Float = 0f,       // -100 ~ 100
    val vignette: Float = 0f,        // 0 ~ 100
    val tint: Float = 0f             // -100 ~ 100
) {
    fun isDefault(): Boolean = brightness == 0f && contrast == 0f && saturation == 0f &&
            warmth == 0f && exposure == 0f && highlights == 0f && shadows == 0f &&
            sharpness == 0f && vignette == 0f && tint == 0f
}

/**
 * 滤镜预设实体，持久化到 Room 数据库
 */
@Entity(tableName = "filter_presets")
data class FilterPreset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val exposure: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val sharpness: Float = 0f,
    val vignette: Float = 0f,
    val tint: Float = 0f,
    val createdAt: Long = System.currentTimeMillis(),
    val isBuiltIn: Boolean = false
) {
    fun toImageParameters(): ImageParameters = ImageParameters(
        brightness = brightness,
        contrast = contrast,
        saturation = saturation,
        warmth = warmth,
        exposure = exposure,
        highlights = highlights,
        shadows = shadows,
        sharpness = sharpness,
        vignette = vignette,
        tint = tint
    )

    companion object {
        fun fromImageParameters(name: String, params: ImageParameters, isBuiltIn: Boolean = false) =
            FilterPreset(
                name = name,
                brightness = params.brightness,
                contrast = params.contrast,
                saturation = params.saturation,
                warmth = params.warmth,
                exposure = params.exposure,
                highlights = params.highlights,
                shadows = params.shadows,
                sharpness = params.sharpness,
                vignette = params.vignette,
                tint = params.tint,
                isBuiltIn = isBuiltIn
            )

        /** 内置滤镜预设 */
        val builtInFilters = listOf(
            FilterPreset(name = "清新", brightness = 10f, contrast = 5f, saturation = 15f, warmth = -10f, isBuiltIn = true),
            FilterPreset(name = "暖阳", brightness = 8f, contrast = 10f, saturation = 5f, warmth = 30f, exposure = 5f, isBuiltIn = true),
            FilterPreset(name = "冷调", brightness = -5f, contrast = 15f, saturation = -10f, warmth = -25f, tint = 10f, isBuiltIn = true),
            FilterPreset(name = "复古", brightness = -5f, contrast = 20f, saturation = -30f, warmth = 15f, vignette = 30f, isBuiltIn = true),
            FilterPreset(name = "黑白", saturation = -100f, contrast = 20f, isBuiltIn = true),
            FilterPreset(name = "电影", brightness = -8f, contrast = 25f, saturation = -15f, warmth = 5f, vignette = 40f, shadows = -10f, isBuiltIn = true),
            FilterPreset(name = "明亮", brightness = 20f, contrast = 5f, saturation = 10f, exposure = 10f, highlights = 15f, isBuiltIn = true),
            FilterPreset(name = "胶片", brightness = 5f, contrast = 15f, saturation = -20f, warmth = 10f, vignette = 20f, tint = 5f, isBuiltIn = true),
        )
    }
}
