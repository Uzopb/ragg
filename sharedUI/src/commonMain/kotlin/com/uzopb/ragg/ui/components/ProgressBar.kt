package com.uzopb.ragg.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.uzopb.ragg.ui.theme.RaggColors

@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val p = progress.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(RaggColors.Pearl200),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(p)
                .height(8.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFF3A3842), Color(0xFF6E6A76)),
                    ),
                ),
        )
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes Б"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${(kb * 10).toInt() / 10.0} КБ"
    val mb = kb / 1024.0
    if (mb < 1024) return "${(mb * 10).toInt() / 10.0} МБ"
    val gb = mb / 1024.0
    return "${(gb * 10).toInt() / 10.0} ГБ"
}
