package com.imagerefine.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.imagerefine.app.data.model.FilterPreset
import com.imagerefine.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterListScreen(
    filters: List<FilterPreset>,
    onDeleteFilter: (FilterPreset) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("我的滤镜", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        }
    ) { paddingValues ->
        if (filters.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FilterVintage,
                        contentDescription = null,
                        tint = TextHint,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "暂无保存的滤镜",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "在编辑照片时调整参数，即可保存为自定义滤镜",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextHint
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filters) { filter ->
                    FilterDetailCard(
                        filter = filter,
                        onDelete = if (!filter.isBuiltIn) {
                            { onDeleteFilter(filter) }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterDetailCard(
    filter: FilterPreset,
    onDelete: (() -> Unit)?
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                color = Primary.copy(
                                    alpha = 0.3f + (filter.saturation + 100f) / 400f,
                                    red = (0.42f + filter.warmth / 300f).coerceIn(0f, 1f),
                                    blue = (0.42f - filter.warmth / 300f).coerceIn(0f, 1f)
                                )
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = filter.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }

                if (onDelete != null) {
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = ErrorRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "内置",
                        tint = TextHint,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 参数概览
            val paramLabels = listOfNotNull(
                if (filter.brightness != 0f) "亮度 ${filter.brightness.toInt()}" else null,
                if (filter.contrast != 0f) "对比 ${filter.contrast.toInt()}" else null,
                if (filter.saturation != 0f) "饱和 ${filter.saturation.toInt()}" else null,
                if (filter.warmth != 0f) "色温 ${filter.warmth.toInt()}" else null,
                if (filter.exposure != 0f) "曝光 ${filter.exposure.toInt()}" else null,
                if (filter.sharpness != 0f) "锐度 ${filter.sharpness.toInt()}" else null,
                if (filter.vignette != 0f) "暗角 ${filter.vignette.toInt()}" else null,
            )

            Text(
                text = paramLabels.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = TextHint,
                maxLines = 2
            )
        }
    }

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除滤镜", color = TextPrimary) },
            text = { Text("确定要删除「${filter.name}」吗？", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text("删除", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消", color = TextSecondary)
                }
            },
            containerColor = Surface
        )
    }
}
