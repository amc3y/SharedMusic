package com.example.sharedmusic.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// У SharedMusic одна цветовая схема — «Dark Black + Pink».
// (Светлая тема и динамические «обои» — post-MVP.)
private val SharedMusicDarkColorScheme = darkColorScheme(
    primary = PinkPrimary,
    onPrimary = PinkOnPrimary,
    primaryContainer = PinkPrimaryContainer,
    onPrimaryContainer = PinkOnPrimaryContainer,
    secondary = PinkSecondary,
    secondaryContainer = PinkSecondaryContainer,
    onSecondaryContainer = PinkOnSecondaryContainer,
    background = BlackBackground,
    onBackground = OnSurfaceLight,
    surface = BlackSurface,
    onSurface = OnSurfaceLight,
    surfaceVariant = BlackSurfaceVariant,
    onSurfaceVariant = OnSurfaceVariantMuted,
    surfaceContainer = BlackSurfaceContainer,
    error = ErrorLight,
)

@Composable
fun SharedMusicTheme(
    // Оставлен для совместимости со старой сигнатурой; тема всегда тёмная
    darkTheme: Boolean = true,
    // Динамический цвет (обои Android 12+) выключен по умолчанию:
    // бренд — чёрный + розовый, обои его не должны перебивать
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        SharedMusicDarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}