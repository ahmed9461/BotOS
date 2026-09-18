package com.ahmed9461.botos.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.ahmed9461.botos.model.ThemeMode

val LocalMotionMillis = staticCompositionLocalOf { 200 }
// Porcelain and neutral graphite, with a restrained iris accent. Launcher artwork is unchanged.
private val LightColors = lightColorScheme(
    primary = Color(0xFF65518F), onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE7F8), onPrimaryContainer = Color(0xFF3F2E61),
    secondary = Color(0xFF5D606E), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEEEFF3), onSecondaryContainer = Color(0xFF363844),
    background = Color(0xFFFAF9F6), onBackground = Color(0xFF24252B),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF24252B),
    surfaceVariant = Color(0xFFF1F0F4), onSurfaceVariant = Color(0xFF62636F),
    outline = Color(0xFF898994), outlineVariant = Color(0xFFE3E2E9),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF8F7FA),
    surfaceContainer = Color(0xFFF1F0F4), surfaceContainerHigh = Color(0xFFEBEAF0),
    surfaceContainerHighest = Color(0xFFE4E3EA), surfaceTint = Color(0xFF65518F),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFCEC3F1), onPrimary = Color(0xFF2E2345),
    primaryContainer = Color(0xFF3B3450), onPrimaryContainer = Color(0xFFEDE7FF),
    secondary = Color(0xFFCBCDD8), onSecondary = Color(0xFF292B35),
    secondaryContainer = Color(0xFF30323B), onSecondaryContainer = Color(0xFFE3E4EB),
    background = Color(0xFF191A1E), onBackground = Color(0xFFF2F1F5),
    surface = Color(0xFF25262C), onSurface = Color(0xFFF2F1F5),
    surfaceVariant = Color(0xFF303138), onSurfaceVariant = Color(0xFFBFC0CC),
    outline = Color(0xFF8E8E9C), outlineVariant = Color(0xFF3E3F49),
    surfaceContainerLowest = Color(0xFF151619), surfaceContainerLow = Color(0xFF202126),
    surfaceContainer = Color(0xFF292A31), surfaceContainerHigh = Color(0xFF303138),
    surfaceContainerHighest = Color(0xFF383941), surfaceTint = Color(0xFFCEC3F1),
)
@Composable
fun BotOsTheme(mode: ThemeMode, reducedMotion: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    CompositionLocalProvider(LocalMotionMillis provides if (reducedMotion) 0 else 200) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
    }
}
