package com.example.sharedmusic.design.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Цветовая схема Material 3 для тёмной/графитовой темы на базе Black (#222323) и Milk (#FFFDF4)
private val SharyDarkColorScheme = darkColorScheme(
    primary = SharyMilk,
    onPrimary = SharyBlack,
    primaryContainer = lerp(SharyBlack, SharyMilk, 0.22f),
    onPrimaryContainer = SharyMilk,
    secondary = lerp(SharyBlack, SharyMilk, 0.16f),
    onSecondary = SharyMilk,
    background = SharyBlack,
    onBackground = SharyMilk,
    surface = lerp(SharyBlack, SharyMilk, 0.10f),
    onSurface = SharyMilk,
    surfaceVariant = lerp(SharyBlack, SharyMilk, 0.16f),
    onSurfaceVariant = SharyMilk.copy(alpha = 0.72f),
    outline = SharyMilk.copy(alpha = 0.16f)
)

// Цветовая схема Material 3 для светлой темы на базе Milk (#FFFDF4) и Black (#222323)
private val SharyLightColorScheme = lightColorScheme(
    primary = SharyBlack,
    onPrimary = SharyMilk,
    primaryContainer = lerp(SharyMilk, SharyBlack, 0.12f),
    onPrimaryContainer = SharyBlack,
    secondary = lerp(SharyMilk, SharyBlack, 0.08f),
    onSecondary = SharyBlack,
    background = SharyMilk,
    onBackground = SharyBlack,
    surface = SharyMilk,
    onSurface = SharyBlack,
    surfaceVariant = lerp(SharyMilk, SharyBlack, 0.06f),
    onSurfaceVariant = SharyBlack.copy(alpha = 0.68f),
    outline = SharyBlack.copy(alpha = 0.14f)
)

// Точка доступа к полупрозрачной монохромной палитре SHARY из любого Composable
object SharyThemeTokens {
    val surfaces: SharySurfacePalette
        @Composable
        @ReadOnlyComposable
        get() = LocalSharySurfacePalette.current
}

// Корневая обёртка темы SHARY с поддержкой 3 режимов: Тёмная, Серая (текущая) и Светлая
@Composable
fun SharyTheme(
    darkTheme: Boolean = true,
    themeMode: SharyThemeMode? = null,
    preset: SharyColorPreset = SharyColorPreset.MILK_BLACK,
    content: @Composable () -> Unit
) {
    val resolvedMode = themeMode ?: if (darkTheme) SharyThemeMode.GRAY else SharyThemeMode.LIGHT

    val surfacePalette = when (preset) {
        SharyColorPreset.MILK_BLACK -> when (resolvedMode) {
            SharyThemeMode.DARK -> TrueDarkMilkBlackSurfacePalette
            SharyThemeMode.GRAY -> DarkMilkBlackSurfacePalette
            SharyThemeMode.LIGHT -> LightMilkBlackSurfacePalette
        }
    }

    val colorScheme = if (surfacePalette.isLight) SharyLightColorScheme else SharyDarkColorScheme

    val view = LocalView.current
    val isPreview = LocalInspectionMode.current
    if (!isPreview) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = surfacePalette.isLight
            controller.isAppearanceLightNavigationBars = surfacePalette.isLight
        }
    }

    CompositionLocalProvider(LocalSharySurfacePalette provides surfacePalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
