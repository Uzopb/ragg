package com.uzopb.ragg.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.uzopb.ragg.ui.theme.RaggColors

/** Фоновая атмосфера демо: перламутровые пятна + лёгкий sheen. */
@Composable
fun PearlAtmosphere(modifier: Modifier = Modifier) {
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFEBE8EF),
                        Color(0xFFD9DDE4),
                        Color(0xFFE8E4EA),
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xF2FFFFFF), Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.1f),
                    radius = size.minDimension * 0.7f,
                ),
                radius = size.minDimension * 0.7f,
                center = Offset(size.width * 0.15f, size.height * 0.1f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x8CD2DCE6), Color.Transparent),
                    center = Offset(size.width * 0.88f, size.height * 0.18f),
                    radius = size.minDimension * 0.55f,
                ),
                radius = size.minDimension * 0.55f,
                center = Offset(size.width * 0.88f, size.height * 0.18f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x73E8DCE4), Color.Transparent),
                    center = Offset(size.width * 0.7f, size.height * 0.85f),
                    radius = size.minDimension * 0.6f,
                ),
                radius = size.minDimension * 0.6f,
                center = Offset(size.width * 0.7f, size.height * 0.85f),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(RaggColors.Pearl100.copy(alpha = 0.15f)),
        )
    }
}
