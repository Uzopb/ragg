package com.uzopb.ragg.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun MenuIcon(modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(size)) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        val y1 = size.toPx() * 0.29f
        val y2 = size.toPx() * 0.5f
        val y3 = size.toPx() * 0.71f
        val left = size.toPx() * 0.17f
        val right = size.toPx() * 0.83f
        val rightShort = size.toPx() * 0.58f
        drawLine(color, Offset(left, y1), Offset(right, y1), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color, Offset(left, y2), Offset(right, y2), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color, Offset(left, y3), Offset(rightShort, y3), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

@Composable
fun CloseIcon(modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(size)) {
        val pad = size.toPx() * 0.25f
        val w = 1.8.dp.toPx()
        drawLine(color, Offset(pad, pad), Offset(size.toPx() - pad, size.toPx() - pad), w, StrokeCap.Round)
        drawLine(color, Offset(size.toPx() - pad, pad), Offset(pad, size.toPx() - pad), w, StrokeCap.Round)
    }
}

@Composable
fun PlusIcon(modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(size)) {
        val mid = size.toPx() / 2
        val pad = size.toPx() * 0.21f
        val w = 1.8.dp.toPx()
        drawLine(color, Offset(mid, pad), Offset(mid, size.toPx() - pad), w, StrokeCap.Round)
        drawLine(color, Offset(pad, mid), Offset(size.toPx() - pad, mid), w, StrokeCap.Round)
    }
}

@Composable
fun SaveIcon(modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(size)) {
        val w = 1.8.dp.toPx()
        val l = size.toPx() * 0.25f
        val r = size.toPx() * 0.75f
        val t = size.toPx() * 0.18f
        val b = size.toPx() * 0.82f
        val fold = size.toPx() * 0.42f
        drawLine(color, Offset(l, t), Offset(fold, t), w, StrokeCap.Round)
        drawLine(color, Offset(fold, t), Offset(r, fold), w, StrokeCap.Round)
        drawLine(color, Offset(r, fold), Offset(r, b), w, StrokeCap.Round)
        drawLine(color, Offset(r, b), Offset(l, b), w, StrokeCap.Round)
        drawLine(color, Offset(l, b), Offset(l, t), w, StrokeCap.Round)
        drawLine(color, Offset(fold, t), Offset(fold, fold), w, StrokeCap.Round)
        drawLine(color, Offset(fold, fold), Offset(r, fold), w, StrokeCap.Round)
        val y1 = size.toPx() * 0.55f
        val y2 = size.toPx() * 0.68f
        drawLine(color, Offset(l + 4.dp.toPx(), y1), Offset(r - 4.dp.toPx(), y1), w, StrokeCap.Round)
        drawLine(color, Offset(l + 4.dp.toPx(), y2), Offset(r - 4.dp.toPx(), y2), w, StrokeCap.Round)
    }
}

@Composable
fun SendIcon(modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(size)) {
        val w = 2.dp.toPx()
        val midY = size.toPx() / 2
        val left = size.toPx() * 0.2f
        val right = size.toPx() * 0.8f
        drawLine(color, Offset(left, midY), Offset(right, midY), w, StrokeCap.Round)
        drawLine(color, Offset(right - size.toPx() * 0.28f, size.toPx() * 0.28f), Offset(right, midY), w, StrokeCap.Round)
        drawLine(color, Offset(right - size.toPx() * 0.28f, size.toPx() * 0.72f), Offset(right, midY), w, StrokeCap.Round)
    }
}

@Composable
fun TrashIcon(modifier: Modifier = Modifier, size: Dp = 18.dp) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(size)) {
        val w = 1.8.dp.toPx()
        val l = size.toPx() * 0.2f
        val r = size.toPx() * 0.8f
        drawLine(color, Offset(l, size.toPx() * 0.3f), Offset(r, size.toPx() * 0.3f), w, StrokeCap.Round)
        drawLine(color, Offset(size.toPx() * 0.35f, size.toPx() * 0.3f), Offset(size.toPx() * 0.35f, size.toPx() * 0.2f), w, StrokeCap.Round)
        drawLine(color, Offset(size.toPx() * 0.65f, size.toPx() * 0.3f), Offset(size.toPx() * 0.65f, size.toPx() * 0.2f), w, StrokeCap.Round)
        drawLine(color, Offset(size.toPx() * 0.35f, size.toPx() * 0.2f), Offset(size.toPx() * 0.65f, size.toPx() * 0.2f), w, StrokeCap.Round)
        drawLine(color, Offset(size.toPx() * 0.28f, size.toPx() * 0.3f), Offset(size.toPx() * 0.32f, size.toPx() * 0.82f), w, StrokeCap.Round)
        drawLine(color, Offset(size.toPx() * 0.72f, size.toPx() * 0.3f), Offset(size.toPx() * 0.68f, size.toPx() * 0.82f), w, StrokeCap.Round)
        drawLine(color, Offset(size.toPx() * 0.32f, size.toPx() * 0.82f), Offset(size.toPx() * 0.68f, size.toPx() * 0.82f), w, StrokeCap.Round)
    }
}

@Composable
fun RefreshIcon(modifier: Modifier = Modifier, size: Dp = 18.dp) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(size)) {
        val w = 1.8.dp.toPx()
        val c = Offset(size.toPx() / 2, size.toPx() / 2)
        val r = size.toPx() * 0.32f
        drawArc(
            color = color,
            startAngle = -20f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(c.x - r, c.y - r),
            size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
            style = Stroke(width = w, cap = StrokeCap.Round),
        )
        val tip = Offset(c.x + r * 0.7f, c.y - r)
        drawLine(color, tip, Offset(tip.x + 4.dp.toPx(), tip.y + 2.dp.toPx()), w, StrokeCap.Round)
        drawLine(color, tip, Offset(tip.x - 1.dp.toPx(), tip.y + 5.dp.toPx()), w, StrokeCap.Round)
    }
}

@Composable
fun DownloadIcon(modifier: Modifier = Modifier, size: Dp = 18.dp) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(size)) {
        val w = 1.8.dp.toPx()
        val mid = size.toPx() / 2
        drawLine(color, Offset(mid, size.toPx() * 0.2f), Offset(mid, size.toPx() * 0.62f), w, StrokeCap.Round)
        drawLine(color, Offset(mid, size.toPx() * 0.62f), Offset(mid - 5.dp.toPx(), size.toPx() * 0.45f), w, StrokeCap.Round)
        drawLine(color, Offset(mid, size.toPx() * 0.62f), Offset(mid + 5.dp.toPx(), size.toPx() * 0.45f), w, StrokeCap.Round)
        drawLine(color, Offset(size.toPx() * 0.25f, size.toPx() * 0.75f), Offset(size.toPx() * 0.75f, size.toPx() * 0.75f), w, StrokeCap.Round)
    }
}

@Composable
fun CheckIcon(modifier: Modifier = Modifier, size: Dp = 14.dp) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(size)) {
        val w = 2.5.dp.toPx()
        drawLine(
            color,
            Offset(size.toPx() * 0.2f, size.toPx() * 0.5f),
            Offset(size.toPx() * 0.42f, size.toPx() * 0.72f),
            w,
            StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(size.toPx() * 0.42f, size.toPx() * 0.72f),
            Offset(size.toPx() * 0.82f, size.toPx() * 0.28f),
            w,
            StrokeCap.Round,
        )
    }
}

@Composable
fun SearchIcon(modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(size)) {
        val w = 1.8.dp.toPx()
        val r = size.toPx() * 0.28f
        val c = Offset(size.toPx() * 0.42f, size.toPx() * 0.42f)
        drawCircle(color, r, c, style = Stroke(width = w))
        drawLine(
            color,
            Offset(c.x + r * 0.7f, c.y + r * 0.7f),
            Offset(size.toPx() * 0.82f, size.toPx() * 0.82f),
            w,
            StrokeCap.Round,
        )
    }
}
