package com.uzopb.ragg.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uzopb.ragg.ui.theme.RaggColors

@Composable
fun IconBtn(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    danger: Boolean = false,
    size: Dp = 40.dp,
    content: @Composable () -> Unit,
) {
    val bg = when {
        active -> RaggColors.Ok.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    val borderColor = when {
        active -> RaggColors.Ok.copy(alpha = 0.25f)
        else -> RaggColors.Pearl300.copy(alpha = 0.7f)
    }
    val tint = when {
        !enabled -> RaggColors.Gray400
        danger -> RaggColors.Danger
        active -> RaggColors.Ok
        else -> RaggColors.Gray800
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick,
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides tint) {
            content()
        }
    }
}

@Composable
fun TextBtn(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        color = when {
            !enabled -> RaggColors.Gray400
            danger -> RaggColors.Danger
            else -> RaggColors.Accent
        },
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    )
}

@Composable
fun PrimaryBtn(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (enabled) {
                    Modifier.background(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFF3A3842), Color(0xFF56525E), Color(0xFF6E6A76)),
                        ),
                    )
                } else {
                    Modifier.background(RaggColors.Pearl300)
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) RaggColors.Pearl50 else RaggColors.Gray500,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
        )
    }
}
