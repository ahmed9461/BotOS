package com.ahmed9461.botos.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.ahmed9461.botos.model.ThemeMode

val LocalMotionMillis = staticCompositionLocalOf { 220 }
private val LightColors = lightColorScheme(
    primary = Color(0xFF73566F), onPrimary = Color.White,
    primaryContainer = Color(0xFFEEDDEC), onPrimaryContainer = Color(0xFF342032),
    secondary = Color(0xFF795B4B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF2DFD3), onSecondaryContainer = Color(0xFF34251D),
    background = Color(0xFFF7F4F1), onBackground = Color(0xFF251F26),
    surface = Color(0xFFFFFBFF), onSurface = Color(0xFF251F26),
    surfaceVariant = Color(0xFFEEE7EB), onSurfaceVariant = Color(0xFF635A64),
    outline = Color(0xFF80727E), outlineVariant = Color(0xFFCFC1CD),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFDEBBDD), onPrimary = Color(0xFF332032),
    primaryContainer = Color(0xFF52394F), onPrimaryContainer = Color(0xFFF5DDF1),
    secondary = Color(0xFFDAB9A6), onSecondary = Color(0xFF34251D),
    secondaryContainer = Color(0xFF4B382E), onSecondaryContainer = Color(0xFFF3DFD4),
    background = Color(0xFF151316), onBackground = Color(0xFFF1E8F0),
    surface = Color(0xFF201D22), onSurface = Color(0xFFF1E8F0),
    surfaceVariant = Color(0xFF2B262E), onSurfaceVariant = Color(0xFFCFC1CD),
    outline = Color(0xFFA897A5), outlineVariant = Color(0xFF5D515F),
)
@Composable
fun BotOsTheme(mode: ThemeMode, reducedMotion: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    CompositionLocalProvider(LocalMotionMillis provides if (reducedMotion) 0 else 220) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
    }
}
