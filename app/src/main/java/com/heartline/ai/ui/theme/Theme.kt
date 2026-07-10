package com.heartline.ai.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val HeartlineBlack = Color(0xFF050506)
val HeartlineInk = Color(0xFF101115)
val HeartlinePanel = Color(0xFF191B20)
val HeartlinePanelHigh = Color(0xFF23252C)
val HeartlineStroke = Color(0xFF343741)
val HeartlineText = Color(0xFFF7F7F9)
val HeartlineMuted = Color(0xFF9EA1AB)
val HeartlineRed = Color(0xFFFF2D16)
val HeartlineOrange = Color(0xFFFF7A18)
val HeartlineViolet = Color(0xFF7B3FF2)
val HeartlineGreen = Color(0xFF80D652)
val HeartlineBlue = Color(0xFF2E8CFF)

private val HeartlineColors: ColorScheme = darkColorScheme(
    primary = HeartlineRed,
    onPrimary = Color.White,
    secondary = HeartlineViolet,
    onSecondary = Color.White,
    tertiary = HeartlineGreen,
    background = HeartlineBlack,
    onBackground = HeartlineText,
    surface = HeartlinePanel,
    onSurface = HeartlineText,
    surfaceVariant = HeartlinePanelHigh,
    onSurfaceVariant = HeartlineMuted,
    outline = HeartlineStroke
)

@Composable
fun HeartlineTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HeartlineColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
