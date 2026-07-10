package com.heartline.ai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Rose = Color(0xFFFF6D8E)
val Blush = Color(0xFFFFD6E2)
val Ink = Color(0xFF171117)
val Wine = Color(0xFF5B2339)
val Cream = Color(0xFFFFF7F1)
val Mint = Color(0xFFA9E6D2)
val Gold = Color(0xFFFFC76B)

private val LightColors: ColorScheme = lightColorScheme(
    primary = Rose,
    onPrimary = Color.White,
    secondary = Wine,
    onSecondary = Color.White,
    tertiary = Mint,
    background = Cream,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFFFEDF3)
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Rose,
    onPrimary = Color.White,
    secondary = Blush,
    onSecondary = Ink,
    tertiary = Gold,
    background = Ink,
    onBackground = Cream,
    surface = Color(0xFF241A22),
    onSurface = Cream,
    surfaceVariant = Color(0xFF382632)
)

@Composable
fun HeartlineTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
