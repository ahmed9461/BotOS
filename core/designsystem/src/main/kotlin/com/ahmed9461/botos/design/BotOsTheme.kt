package com.ahmed9461.botos.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ahmed9461.botos.model.ThemeMode

val LocalMotionMillis = staticCompositionLocalOf { 200 }
// Owner-requested messenger refinement, 2026-09-27. Launcher artwork is unchanged.
private val LightColors = lightColorScheme(
    primary = Color(0xFF6554C0), onPrimary = Color.White,
    primaryContainer = Color(0xFFEBE8FC), onPrimaryContainer = Color(0xFF30244F),
    secondary = Color(0xFF5C6070), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEAECF3), onSecondaryContainer = Color(0xFF303441),
    background = Color(0xFFF4F5F9), onBackground = Color(0xFF20212B),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF20212B),
    surfaceVariant = Color(0xFFEEF0F6), onSurfaceVariant = Color(0xFF626574),
    outline = Color(0xFF878B9B), outlineVariant = Color(0xFFE1E3EC),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF8F9FC),
    surfaceContainer = Color(0xFFEEF0F6), surfaceContainerHigh = Color(0xFFE8EAF2),
    surfaceContainerHighest = Color(0xFFDFE2EC), surfaceTint = Color(0xFF6554C0),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFBDB2FF), onPrimary = Color(0xFF25194B),
    primaryContainer = Color(0xFF34285B), onPrimaryContainer = Color(0xFFF0EBFF),
    secondary = Color(0xFFC4C6D4), onSecondary = Color(0xFF272A36),
    secondaryContainer = Color(0xFF2B2D39), onSecondaryContainer = Color(0xFFE3E5F0),
    background = Color(0xFF101115), onBackground = Color(0xFFF3F3F8),
    surface = Color(0xFF1C1D24), onSurface = Color(0xFFF3F3F8),
    surfaceVariant = Color(0xFF272933), onSurfaceVariant = Color(0xFFB4B5C2),
    outline = Color(0xFF858795), outlineVariant = Color(0xFF32343F),
    surfaceContainerLowest = Color(0xFF0D0E12), surfaceContainerLow = Color(0xFF17181E),
    surfaceContainer = Color(0xFF22232C), surfaceContainerHigh = Color(0xFF292B35),
    surfaceContainerHighest = Color(0xFF333540), surfaceTint = Color(0xFFBDB2FF),
)
private val MessengerShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
@Composable
fun BotOsTheme(mode: ThemeMode, reducedMotion: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    CompositionLocalProvider(LocalMotionMillis provides if (reducedMotion) 0 else 200) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, shapes = MessengerShapes, content = content)
    }
}
