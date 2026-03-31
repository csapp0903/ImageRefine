package com.imagerefine.app.viewmodel

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.imagerefine.app.data.db.AppDatabase
import com.imagerefine.app.data.model.FilterPreset
import com.imagerefine.app.data.model.ImageParameters
import com.imagerefine.app.data.remote.AiConfig
import com.imagerefine.app.data.remote.AiService
import com.imagerefine.app.data.remote.model.BeautyOptions
import com.imagerefine.app.data.repository.FilterRepository
import com.imagerefine.app.engine.AiProcessor
import com.imagerefine.app.engine.ImageProcessor
import com.imagerefine.app.engine.StyleExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class EditViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = FilterRepository(db.filterDao())
    private val aiProcessor = AiProcessor(AiService.getInstance())

    // 原始图片
    private val _originalBitmap = MutableStateFlow<Bitmap?>(null)
    val originalBitmap: StateFlow<Bitmap?> = _originalBitmap.asStateFlow()

    // 处理后的图片
    private val _processedBitmap = MutableStateFlow<Bitmap?>(null)
    val processedBitmap: StateFlow<Bitmap?> = _processedBitmap.asStateFlow()

    // 当前参数
    private val _parameters = MutableStateFlow(ImageParameters())
    val parameters: StateFlow<ImageParameters> = _parameters.asStateFlow()

    // 是否显示原图对比
    private val _showOriginal = MutableStateFlow(false)
    val showOriginal: StateFlow<Boolean> = _showOriginal.asStateFlow()

    // 所有滤镜
    val allFilters: StateFlow<List<FilterPreset>> = repository.getAllFilters()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 操作状态
    private val _uiMessage = MutableSharedFlow<String>()
    val uiMessage: SharedFlow<String> = _uiMessage.asSharedFlow()

    // 正在处理中
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    // AI 相关状态
    private val _isAiProcessing = MutableStateFlow(false)
    val isAiProcessing: StateFlow<Boolean> = _isAiProcessing.asStateFlow()

    private val _aiStatusText = MutableStateFlow("")
    val aiStatusText: StateFlow<String> = _aiStatusText.asStateFlow()

    // AI 抠图结果（前景）
    private val _segmentedBitmap = MutableStateFlow<Bitmap?>(null)
    val segmentedBitmap: StateFlow<Bitmap?> = _segmentedBitmap.asStateFlow()

    // AI 服务器地址
    private val _serverUrl = MutableStateFlow(AiConfig.BASE_URL)
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    // 美颜参数
    private val _beautyOptions = MutableStateFlow(BeautyOptions())
    val beautyOptions: StateFlow<BeautyOptions> = _beautyOptions.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.initBuiltInFilters()
        }
    }

    /** 加载图片 */
    fun loadImage(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val inputStream = context.contentResolver.openInputStream(uri)
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                // 先读取尺寸
                BitmapFactory.decodeStream(
                    context.contentResolver.openInputStream(uri),
                    null,
                    options
                )

                // 限制最大尺寸以避免 OOM
                val maxDimension = 2048
                var sampleSize = 1
                while (options.outWidth / sampleSize > maxDimension ||
                    options.outHeight / sampleSize > maxDimension
                ) {
                    sampleSize *= 2
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }

                val bitmap = BitmapFactory.decodeStream(
                    context.contentResolver.openInputStream(uri),
                    null,
                    decodeOptions
                )

                bitmap?.let {
                    _originalBitmap.value = it
                    _parameters.value = ImageParameters()
                    _processedBitmap.value = it.copy(Bitmap.Config.ARGB_8888, false)
                }
            } catch (e: Exception) {
                _uiMessage.emit("加载图片失败: ${e.message}")
            }
        }
    }

    /** 更新参数并重新处理 */
    fun updateParameter(update: (ImageParameters) -> ImageParameters) {
        val newParams = update(_parameters.value)
        _parameters.value = newParams
        applyCurrentParameters()
    }

    /** 应用当前参数到原图 */
    private fun applyCurrentParameters() {
        val source = _originalBitmap.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            _isProcessing.value = true
            try {
                val result = ImageProcessor.applyParameters(source, _parameters.value)
                _processedBitmap.value = result
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /** 应用滤镜预设 */
    fun applyFilter(filter: FilterPreset) {
        _parameters.value = filter.toImageParameters()
        applyCurrentParameters()
    }

    /** 提取当前图片风格 */
    fun extractStyle() {
        val bitmap = _originalBitmap.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            _isProcessing.value = true
            try {
                val style = StyleExtractor.extractStyle(bitmap)
                _parameters.value = style
                val result = ImageProcessor.applyParameters(bitmap, style)
                _processedBitmap.value = result
                _uiMessage.emit("风格已提取")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /** 从参考图提取风格差异并应用 */
    fun extractStyleFromReference(referenceUri: Uri) {
        val source = _originalBitmap.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            try {
                val context = getApplication<Application>()
                val refBitmap = BitmapFactory.decodeStream(
                    context.contentResolver.openInputStream(referenceUri)
                )
                if (refBitmap != null) {
                    val style = withContext(Dispatchers.Default) {
                        StyleExtractor.extractStyle(refBitmap)
                    }
                    _parameters.value = style
                    val result = withContext(Dispatchers.Default) {
                        ImageProcessor.applyParameters(source, style)
                    }
                    _processedBitmap.value = result
                    _uiMessage.emit("已提取参考图风格")
                }
            } catch (e: Exception) {
                _uiMessage.emit("提取风格失败: ${e.message}")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /** 保存当前参数为滤镜 */
    fun saveCurrentAsFilter(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val filter = FilterPreset.fromImageParameters(name, _parameters.value)
            repository.saveFilter(filter)
            _uiMessage.emit("滤镜「$name」已保存")
        }
    }

    /** 删除滤镜 */
    fun deleteFilter(filter: FilterPreset) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteFilter(filter)
            _uiMessage.emit("滤镜已删除")
        }
    }

    /** 重置参数 */
    fun resetParameters() {
        _parameters.value = ImageParameters()
        _originalBitmap.value?.let {
            _processedBitmap.value = it.copy(Bitmap.Config.ARGB_8888, false)
        }
    }

    /** 切换原图对比 */
    fun toggleShowOriginal(show: Boolean) {
        _showOriginal.value = show
    }

    /** 保存图片到相册 */
    fun saveImage() {
        val bitmap = _processedBitmap.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val filename = "ImageRefine_${System.currentTimeMillis()}.jpg"

                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                            MediaStore.Images.Media.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES + "/ImageRefine"
                        )
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                ) ?: throw IOException("无法创建媒体文件")

                resolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }

                _uiMessage.emit("照片已保存到相册")
            } catch (e: Exception) {
                _uiMessage.emit("保存失败: ${e.message}")
            }
        }
    }

    // ==================== AI 功能 ====================

    /** 更新 AI 服务器地址 */
    fun updateServerUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isNotEmpty()) {
            val finalUrl = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
            AiConfig.BASE_URL = finalUrl
            AiService.resetInstance()
            _serverUrl.value = finalUrl
        }
    }

    /** AI 智能美化 - 云端增强图片 */
    fun aiAutoEnhance() {
        val bitmap = _originalBitmap.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isAiProcessing.value = true
            _aiStatusText.value = "AI 正在分析图片..."
            try {
                aiProcessor.smartEnhance(bitmap)
                    .onSuccess { resultBitmap ->
                        _processedBitmap.value = resultBitmap
                        _uiMessage.emit("AI 智能美化完成")
                        _aiStatusText.value = "美化完成"
                    }
                    .onFailure { e ->
                        _uiMessage.emit("AI 美化失败: ${e.message}")
                        _aiStatusText.value = "处理失败"
                    }
            } finally {
                _isAiProcessing.value = false
            }
        }
    }

    /** AI 风格迁移 - 选择参考图，将其风格迁移到当前图片 */
    fun aiStyleTransfer(styleUri: Uri) {
        val content = _originalBitmap.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isAiProcessing.value = true
            _aiStatusText.value = "AI 风格迁移中..."
            try {
                val context = getApplication<Application>()
                val styleBitmap = BitmapFactory.decodeStream(
                    context.contentResolver.openInputStream(styleUri)
                )
                if (styleBitmap == null) {
                    _uiMessage.emit("无法加载风格图片")
                    return@launch
                }
                aiProcessor.styleTransfer(content, styleBitmap)
                    .onSuccess { result ->
                        _processedBitmap.value = result
                        _uiMessage.emit("风格迁移完成")
                        _aiStatusText.value = "迁移完成"
                    }
                    .onFailure { e ->
                        _uiMessage.emit("风格迁移失败: ${e.message}")
                        _aiStatusText.value = "处理失败"
                    }
            } catch (e: Exception) {
                _uiMessage.emit("风格迁移失败: ${e.message}")
                _aiStatusText.value = "处理失败"
            } finally {
                _isAiProcessing.value = false
            }
        }
    }

    /** AI 智能抠图 */
    fun aiRemoveBackground() {
        val bitmap = _originalBitmap.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isAiProcessing.value = true
            _aiStatusText.value = "AI 抠图中..."
            try {
                aiProcessor.removeBackground(bitmap)
                    .onSuccess { result ->
                        _segmentedBitmap.value = result
                        _processedBitmap.value = result
                        _uiMessage.emit("抠图完成")
                        _aiStatusText.value = "抠图完成"
                    }
                    .onFailure { e ->
                        _uiMessage.emit("抠图失败: ${e.message}")
                        _aiStatusText.value = "处理失败"
                    }
            } finally {
                _isAiProcessing.value = false
            }
        }
    }

    /** 将抠图结果与新背景合成 */
    fun aiCompositeBackground(backgroundUri: Uri) {
        val foreground = _segmentedBitmap.value ?: run {
            viewModelScope.launch { _uiMessage.emit("请先进行AI抠图") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isAiProcessing.value = true
            _aiStatusText.value = "合成背景中..."
            try {
                val context = getApplication<Application>()
                val bgBitmap = BitmapFactory.decodeStream(
                    context.contentResolver.openInputStream(backgroundUri)
                )
                if (bgBitmap == null) {
                    _uiMessage.emit("无法加载背景图片")
                    return@launch
                }
                val result = withContext(Dispatchers.Default) {
                    aiProcessor.compositeWithBackground(foreground, bgBitmap)
                }
                _processedBitmap.value = result
                _uiMessage.emit("背景替换完成")
                _aiStatusText.value = "合成完成"
            } catch (e: Exception) {
                _uiMessage.emit("合成失败: ${e.message}")
                _aiStatusText.value = "处理失败"
            } finally {
                _isAiProcessing.value = false
            }
        }
    }

    /** AI 人像美颜 */
    fun aiBeautyFace() {
        val bitmap = _originalBitmap.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isAiProcessing.value = true
            _aiStatusText.value = "AI 美颜处理中..."
            try {
                aiProcessor.beautyFace(bitmap, _beautyOptions.value)
                    .onSuccess { result ->
                        _processedBitmap.value = result
                        _uiMessage.emit("美颜完成")
                        _aiStatusText.value = "美颜完成"
                    }
                    .onFailure { e ->
                        _uiMessage.emit("美颜失败: ${e.message}")
                        _aiStatusText.value = "处理失败"
                    }
            } finally {
                _isAiProcessing.value = false
            }
        }
    }

    /** 更新美颜参数 */
    fun updateBeautyOptions(update: (BeautyOptions) -> BeautyOptions) {
        _beautyOptions.value = update(_beautyOptions.value)
    }
}
