package com.uzopb.ragg.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Палитра демо: перламутр / серый; бренд без «AI-фиолетового». */
object RaggColors {
    val Pearl50 = Color(0xFFFAF9FB)
    val Pearl100 = Color(0xFFF2F0F4)
    val Pearl200 = Color(0xFFE4E1E8)
    val Pearl300 = Color(0xFFCDC8D2)
    val Gray400 = Color(0xFF9A96A0)
    val Gray500 = Color(0xFF6F6B75)
    val Gray700 = Color(0xFF3E3B44)
    val Gray800 = Color(0xFF2A2830)
    val Gray900 = Color(0xFF16151A)
    val Ink = Color(0xFF1A1820)
    val Muted = Color(0xFF6A6670)
    val Accent = Color(0xFF4A4752)
    val Ok = Color(0xFF4D6B5C)
    val Warn = Color(0xFF7A6548)
    val Danger = Color(0xFF7A4A4A)
    val Surface = Color(0xF5F7F5F8)
    val SurfaceStrong = Color(0xE8FFFFFF)
}

private val RaggColorScheme = lightColorScheme(
    primary = RaggColors.Accent,
    onPrimary = RaggColors.Pearl50,
    secondary = RaggColors.Gray700,
    onSecondary = RaggColors.Pearl50,
    background = RaggColors.Pearl100,
    onBackground = RaggColors.Ink,
    surface = RaggColors.SurfaceStrong,
    onSurface = RaggColors.Ink,
    surfaceVariant = RaggColors.Pearl200,
    onSurfaceVariant = RaggColors.Muted,
    outline = RaggColors.Pearl300,
    error = RaggColors.Danger,
)

private val DisplayFamily = FontFamily.SansSerif
private val BodyFamily = FontFamily.SansSerif

private val RaggTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 40.sp,
        letterSpacing = 1.2.sp,
        color = RaggColors.Ink,
    ),
    displayMedium = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 0.8.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = RaggColors.Muted,
    ),
    labelLarge = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp,
    ),
)

@Composable
fun RaggTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RaggColorScheme,
        typography = RaggTypography,
        content = content,
    )
}
