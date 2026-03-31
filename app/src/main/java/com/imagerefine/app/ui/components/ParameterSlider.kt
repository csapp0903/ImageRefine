package com.imagerefine.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.imagerefine.app.ui.theme.*

@Composable
fun ParameterSlider(
    label: String,
    value: Float,
    minValue: Float = -100f,
    maxValue: Float = 100f,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Text(
                text = "${value.toInt()}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (value != 0f) Primary else TextHint,
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.End
            )
        }

        Slider(
            value = value,
            onValueChange = { onValueChange(it) },
            valueRange = minValue..maxValue,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Primary,
                activeTrackColor = SliderActiveTrack,
                inactiveTrackColor = SliderTrack
            )
        )
    }
}
