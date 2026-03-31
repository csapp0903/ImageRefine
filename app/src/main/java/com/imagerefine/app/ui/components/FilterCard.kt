package com.imagerefine.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.imagerefine.app.data.model.FilterPreset
import com.imagerefine.app.ui.theme.*

@Composable
fun FilterCard(
    filter: FilterPreset,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(100.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
    ) {
        Box {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 滤镜预览色块
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            color = Primary.copy(
                                alpha = 0.3f + (filter.saturation + 100f) / 400f,
                                red = (0.42f + filter.warmth / 300f).coerceIn(0f, 1f),
                                blue = (0.42f - filter.warmth / 300f).coerceIn(0f, 1f)
                            )
                        )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = filter.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    maxLines = 1
                )
            }

            // 删除按钮（只对非内置滤镜显示）
            if (onDelete != null && !filter.isBuiltIn) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "删除",
                        modifier = Modifier.size(14.dp),
                        tint = TextHint
                    )
                }
            }
        }
    }
}
