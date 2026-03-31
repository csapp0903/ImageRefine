package com.imagerefine.app.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.imagerefine.app.data.model.FilterPreset
import com.imagerefine.app.data.model.ImageParameters
import com.imagerefine.app.data.remote.model.BeautyOptions
import com.imagerefine.app.ui.components.FilterCard
import com.imagerefine.app.ui.components.ParameterSlider
import com.imagerefine.app.ui.components.SaveFilterDialog
import com.imagerefine.app.ui.theme.*

enum class EditTab { ADJUST, FILTER, STYLE, AI }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    originalBitmap: Bitmap?,
    processedBitmap: Bitmap?,
    parameters: ImageParameters,
    filters: List<FilterPreset>,
    showOriginal: Boolean,
    isProcessing: Boolean,
    isAiProcessing: Boolean,
    aiStatusText: String,
    beautyOptions: BeautyOptions,
    serverUrl: String,
    onParameterChange: (update: (ImageParameters) -> ImageParameters) -> Unit,
    onApplyFilter: (FilterPreset) -> Unit,
    onSaveFilter: (String) -> Unit,
    onDeleteFilter: (FilterPreset) -> Unit,
    onExtractStyle: () -> Unit,
    onExtractFromReference: (Uri) -> Unit,
    onReset: () -> Unit,
    onSaveImage: () -> Unit,
    onToggleOriginal: (Boolean) -> Unit,
    onBack: () -> Unit,
    // AI callbacks
    onAiAutoEnhance: () -> Unit,
    onAiStyleTransfer: (Uri) -> Unit,
    onAiRemoveBackground: () -> Unit,
    onAiCompositeBackground: (Uri) -> Unit,
    onAiBeautyFace: () -> Unit,
    onUpdateBeautyOptions: ((BeautyOptions) -> BeautyOptions) -> Unit,
    onUpdateServerUrl: (String) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(EditTab.ADJUST) }
    var showSaveDialog by remember { mutableStateOf(false) }

    val refLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onExtractFromReference(it) }
    }

    // AI 风格迁移图片选择
    val aiStyleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onAiStyleTransfer(it) }
    }

    // AI 背景替换图片选择
    val bgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onAiCompositeBackground(it) }
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("编辑照片", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = onReset) {
                        Icon(Icons.Default.Refresh, "重置", tint = TextSecondary)
                    }
                    IconButton(onClick = { showSaveDialog = true }) {
                        Icon(Icons.Default.BookmarkAdd, "保存滤镜", tint = Primary)
                    }
                    IconButton(onClick = onSaveImage) {
                        Icon(Icons.Default.SaveAlt, "保存照片", tint = SuccessGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 图片预览区
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onToggleOriginal(true)
                                tryAwaitRelease()
                                onToggleOriginal(false)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val bitmapToShow = if (showOriginal) originalBitmap else processedBitmap
                if (bitmapToShow != null) {
                    Image(
                        bitmap = bitmapToShow.asImageBitmap(),
                        contentDescription = "预览",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text("请选择照片", color = TextHint)
                }

                // 处理中指示器
                if (isProcessing || isAiProcessing) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            color = Primary
                        )
                        if (isAiProcessing && aiStatusText.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = aiStatusText,
                                color = TextPrimary,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier
                                    .background(
                                        color = Background.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // 原图对比提示
                if (showOriginal) {
                    Text(
                        text = "原图",
                        color = TextPrimary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .background(
                                color = Background.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Tab 切换
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = Surface,
                contentColor = Primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                        color = Primary
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == EditTab.ADJUST,
                    onClick = { selectedTab = EditTab.ADJUST },
                    text = { Text("参数调节") },
                    selectedContentColor = Primary,
                    unselectedContentColor = TextSecondary
                )
                Tab(
                    selected = selectedTab == EditTab.FILTER,
                    onClick = { selectedTab = EditTab.FILTER },
                    text = { Text("滤镜") },
                    selectedContentColor = Primary,
                    unselectedContentColor = TextSecondary
                )
                Tab(
                    selected = selectedTab == EditTab.STYLE,
                    onClick = { selectedTab = EditTab.STYLE },
                    text = { Text("风格提取") },
                    selectedContentColor = Primary,
                    unselectedContentColor = TextSecondary
                )
                Tab(
                    selected = selectedTab == EditTab.AI,
                    onClick = { selectedTab = EditTab.AI },
                    text = { Text("AI") },
                    selectedContentColor = Secondary,
                    unselectedContentColor = TextSecondary
                )
            }

            // 底部操作区
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(Surface)
            ) {
                when (selectedTab) {
                    EditTab.ADJUST -> AdjustPanel(parameters, onParameterChange)
                    EditTab.FILTER -> FilterPanel(filters, onApplyFilter, onDeleteFilter)
                    EditTab.STYLE -> StylePanel(
                        onExtractStyle = onExtractStyle,
                        onSelectReference = { refLauncher.launch("image/*") }
                    )
                    EditTab.AI -> AiPanel(
                        isAiProcessing = isAiProcessing,
                        beautyOptions = beautyOptions,
                        serverUrl = serverUrl,
                        onAiAutoEnhance = onAiAutoEnhance,
                        onAiStyleTransfer = { aiStyleLauncher.launch("image/*") },
                        onAiRemoveBackground = onAiRemoveBackground,
                        onAiCompositeBackground = { bgLauncher.launch("image/*") },
                        onAiBeautyFace = onAiBeautyFace,
                        onUpdateBeautyOptions = onUpdateBeautyOptions,
                        onUpdateServerUrl = onUpdateServerUrl
                    )
                }
            }
        }
    }

    if (showSaveDialog) {
        SaveFilterDialog(
            onDismiss = { showSaveDialog = false },
            onConfirm = { name ->
                onSaveFilter(name)
                showSaveDialog = false
            }
        )
    }
}

@Composable
private fun AdjustPanel(
    parameters: ImageParameters,
    onParameterChange: (update: (ImageParameters) -> ImageParameters) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        ParameterSlider(
            label = "亮度",
            value = parameters.brightness,
            onValueChange = { v -> onParameterChange { it.copy(brightness = v) } }
        )
        ParameterSlider(
            label = "对比度",
            value = parameters.contrast,
            onValueChange = { v -> onParameterChange { it.copy(contrast = v) } }
        )
        ParameterSlider(
            label = "饱和度",
            value = parameters.saturation,
            onValueChange = { v -> onParameterChange { it.copy(saturation = v) } }
        )
        ParameterSlider(
            label = "曝光",
            value = parameters.exposure,
            onValueChange = { v -> onParameterChange { it.copy(exposure = v) } }
        )
        ParameterSlider(
            label = "色温",
            value = parameters.warmth,
            onValueChange = { v -> onParameterChange { it.copy(warmth = v) } }
        )
        ParameterSlider(
            label = "高光",
            value = parameters.highlights,
            onValueChange = { v -> onParameterChange { it.copy(highlights = v) } }
        )
        ParameterSlider(
            label = "阴影",
            value = parameters.shadows,
            onValueChange = { v -> onParameterChange { it.copy(shadows = v) } }
        )
        ParameterSlider(
            label = "锐度",
            value = parameters.sharpness,
            onValueChange = { v -> onParameterChange { it.copy(sharpness = v) } }
        )
        ParameterSlider(
            label = "暗角",
            value = parameters.vignette,
            minValue = 0f,
            onValueChange = { v -> onParameterChange { it.copy(vignette = v) } }
        )
        ParameterSlider(
            label = "色调",
            value = parameters.tint,
            onValueChange = { v -> onParameterChange { it.copy(tint = v) } }
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun FilterPanel(
    filters: List<FilterPreset>,
    onApplyFilter: (FilterPreset) -> Unit,
    onDeleteFilter: (FilterPreset) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "内置滤镜",
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val builtIn = filters.filter { it.isBuiltIn }
            items(builtIn) { filter ->
                FilterCard(
                    filter = filter,
                    onClick = { onApplyFilter(filter) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "我的滤镜",
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val userFilters = filters.filter { !it.isBuiltIn }
        if (userFilters.isEmpty()) {
            Text(
                text = "暂无自定义滤镜，调整参数后可保存为滤镜",
                style = MaterialTheme.typography.bodySmall,
                color = TextHint
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(userFilters) { filter ->
                    FilterCard(
                        filter = filter,
                        onClick = { onApplyFilter(filter) },
                        onDelete = { onDeleteFilter(filter) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StylePanel(
    onExtractStyle: () -> Unit,
    onSelectReference: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "风格提取",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "分析照片色彩特征，自动生成滤镜参数",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onExtractStyle,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
            Icon(Icons.Default.AutoFixHigh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("分析当前照片风格")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onSelectReference,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Secondary)
        ) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("从参考图提取风格")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "长按图片可对比原图效果",
            style = MaterialTheme.typography.labelSmall,
            color = TextHint
        )
    }
}

@Composable
private fun AiPanel(
    isAiProcessing: Boolean,
    beautyOptions: BeautyOptions,
    serverUrl: String,
    onAiAutoEnhance: () -> Unit,
    onAiStyleTransfer: () -> Unit,
    onAiRemoveBackground: () -> Unit,
    onAiCompositeBackground: () -> Unit,
    onAiBeautyFace: () -> Unit,
    onUpdateBeautyOptions: ((BeautyOptions) -> BeautyOptions) -> Unit,
    onUpdateServerUrl: (String) -> Unit
) {
    var showServerConfig by remember { mutableStateOf(false) }
    var showBeautyConfig by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // 服务器配置入口
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AI 智能处理",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )
            TextButton(onClick = { showServerConfig = !showServerConfig }) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = TextSecondary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("服务器", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }

        // 服务器地址配置（折叠）
        if (showServerConfig) {
            var urlInput by remember { mutableStateOf(serverUrl) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("服务器地址") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = DividerColor,
                        cursorColor = Primary
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onUpdateServerUrl(urlInput) },
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text("保存", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // AI 智能美化
        AiActionButton(
            icon = Icons.Default.AutoAwesome,
            title = "AI 智能美化",
            subtitle = "自动分析场景，推荐最优参数",
            enabled = !isAiProcessing,
            onClick = onAiAutoEnhance
        )

        Spacer(modifier = Modifier.height(8.dp))

        // AI 风格迁移
        AiActionButton(
            icon = Icons.Default.Style,
            title = "AI 风格迁移",
            subtitle = "选择参考图，深度迁移其艺术风格",
            enabled = !isAiProcessing,
            onClick = onAiStyleTransfer
        )

        Spacer(modifier = Modifier.height(8.dp))

        // AI 智能抠图
        AiActionButton(
            icon = Icons.Default.ContentCut,
            title = "AI 智能抠图",
            subtitle = "自动识别主体，移除背景",
            enabled = !isAiProcessing,
            onClick = onAiRemoveBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 背景替换（需要先抠图）
        AiActionButton(
            icon = Icons.Default.Wallpaper,
            title = "替换背景",
            subtitle = "选择新背景图片，与抠图结果合成",
            enabled = !isAiProcessing,
            onClick = onAiCompositeBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        // AI 人像美颜
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AiActionButton(
                icon = Icons.Default.Face,
                title = "AI 人像美颜",
                subtitle = "磨皮 ${beautyOptions.smooth.toInt()} / 美白 ${beautyOptions.whiten.toInt()}",
                enabled = !isAiProcessing,
                onClick = onAiBeautyFace,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showBeautyConfig = !showBeautyConfig }) {
                Icon(Icons.Default.Tune, contentDescription = "美颜参数", tint = TextSecondary)
            }
        }

        // 美颜参数面板（折叠）
        if (showBeautyConfig) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    BeautySlider("磨皮", beautyOptions.smooth) { v ->
                        onUpdateBeautyOptions { it.copy(smooth = v) }
                    }
                    BeautySlider("美白", beautyOptions.whiten) { v ->
                        onUpdateBeautyOptions { it.copy(whiten = v) }
                    }
                    BeautySlider("瘦脸", beautyOptions.thinFace) { v ->
                        onUpdateBeautyOptions { it.copy(thinFace = v) }
                    }
                    BeautySlider("大眼", beautyOptions.bigEye) { v ->
                        onUpdateBeautyOptions { it.copy(bigEye = v) }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun AiActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) SurfaceVariant else SurfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) Secondary else TextHint,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) TextPrimary else TextHint
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextHint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun BeautySlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            modifier = Modifier.width(36.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..100f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Secondary,
                activeTrackColor = Secondary,
                inactiveTrackColor = SliderTrack
            )
        )
        Text(
            text = "${value.toInt()}",
            style = MaterialTheme.typography.labelSmall,
            color = TextHint,
            modifier = Modifier.width(28.dp)
        )
    }
}
