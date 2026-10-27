package com.paisafloww.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFA78BFA),
    onPrimary = Color(0xFF1A0F3D),
    primaryContainer = Color(0xFF2E2552),
    onPrimaryContainer = Color(0xFFE6DDFF),
    secondary = Color(0xFF2DD4BF),
    onSecondary = Color(0xFF00201C),
    background = Color(0xFF0B1020),
    onBackground = Color(0xFFEAF0FF),
    surface = Color(0xFF141B31),
    onSurface = Color(0xFFEAF0FF),
    surfaceVariant = Color(0xFF1E2744),
    onSurfaceVariant = Color(0xFFA9B4D0),
    error = Color(0xFFFF7A8A),
    outline = Color(0xFF3A4570)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF6D4AFF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6DDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF0E8F80),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF5F6FB),
    onBackground = Color(0xFF12172B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF12172B),
    surfaceVariant = Color(0xFFE8EAF6),
    onSurfaceVariant = Color(0xFF4A5170),
    error = Color(0xFFD93A4F),
    outline = Color(0xFFB5BBD6)
)

/** Semantic colours: money coming in is teal, money going out is coral/red. */
val ColorScheme.income: Color get() = secondary
val ColorScheme.expense: Color get() = error

@Composable
fun PaisaTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = Typography(),
        content = content
    )
}
