package com.example.sharedmusic.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// Фирменная монохромная палитра SHARY: чисто Milk (#FFFDF4) и Black (#222323)
val SharyMilk = Color(0xFFFFFDF4)
val SharyBlack = Color(0xFF222323)

enum class SharyColorPreset {
    MILK_BLACK
}

// Три режима темы оформления:
// 1) DARK — глубокая контрастная тёмная тема без излишней серой пелены;
// 2) GRAY — графитовая полупрозрачная тема («та, что сейчас»);
// 3) LIGHT — светлая молочная тема на базе Milk (#FFFDF4) и Black (#222323).
enum class SharyThemeMode(val storageKey: String, val titleRu: String) {
    DARK("dark", "Тёмная"),
    GRAY("gray", "Серая"),
    LIGHT("light", "Светлая");

    companion object {
        fun fromKey(key: String?): SharyThemeMode {
            return entries.firstOrNull { it.storageKey == key } ?: GRAY
        }
    }
}

// Набор цветов полупрозрачных матовых областей, диалогов, винилового плеера, комнаты и векторной анимации
@Immutable
data class SharySurfacePalette(
    val isLight: Boolean,
    val glassCloudAlpha: Float,
    val backgroundBase: Color,
    val barFill: Color,
    val barBorder: Color,
    val middleFill: Color,
    val middleBorder: Color,
    val milkShimmerSpot: Color,
    val navIconInactive: Color,
    val navIconSelected: Color,
    val navSelectionCircleSelected: Color,
    val navSelectionCirclePressed: Color,
    val transportPrimaryIcon: Color,
    val transportSecondaryIcon: Color,
    val addCircleContainer: Color,
    val addCircleContainerPressed: Color,
    val addCircleIcon: Color,
    val logoPrimaryBar: Color,
    val logoSecondaryBar: Color,
    val placeholderCoverFill: Color,
    val placeholderLogoPrimary: Color,
    val placeholderLogoSecondary: Color,
    val songRowFill: Color,
    val songRowActiveFill: Color,
    val songRowBorder: Color,
    val recentChipFill: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val heartActive: Color,
    // Диалоги и поля ввода
    val dialogFill: Color,
    val dialogBorder: Color,
    val fieldFill: Color,
    val fieldBorder: Color,
    // Цвета винилового проигрывателя и комнаты
    val playerAccent: Color,
    val onAccent: Color,
    val playerCenterGlow: Color,
    val playerTrackInactive: Color,
    val vinylDiscSurface: Color,
    val vinylGrooveStroke: Color,
    // Цвета объёмной векторной анимации (строго на базе Milk и Black)
    val vibeDeepNebula: Color,
    val vibeDeepAura: Color,
    val vibeRayGlow: Color,
    val vibeMidSilk: Color,
    val vibeBrightCrest: Color
)

// 1. Глубокая Тёмная тема (контрастный угольно-чёрный без лишней серости)
val TrueDarkMilkBlackSurfacePalette = SharySurfacePalette(
    isLight = false,
    glassCloudAlpha = 0.28f,
    backgroundBase = lerp(SharyBlack, Color.Black, 0.58f),
    barFill = lerp(SharyBlack, Color.Black, 0.36f).copy(alpha = 0.84f),
    barBorder = SharyMilk.copy(alpha = 0.14f),
    middleFill = lerp(SharyBlack, Color.Black, 0.34f).copy(alpha = 0.86f),
    middleBorder = SharyMilk.copy(alpha = 0.15f),
    milkShimmerSpot = SharyMilk.copy(alpha = 0.03f),
    navIconInactive = SharyMilk.copy(alpha = 0.52f),
    navIconSelected = SharyMilk,
    navSelectionCircleSelected = SharyMilk.copy(alpha = 0.16f),
    navSelectionCirclePressed = SharyMilk.copy(alpha = 0.10f),
    transportPrimaryIcon = SharyMilk,
    transportSecondaryIcon = SharyMilk.copy(alpha = 0.48f),
    addCircleContainer = lerp(SharyBlack, SharyMilk, 0.20f),
    addCircleContainerPressed = lerp(SharyBlack, SharyMilk, 0.32f),
    addCircleIcon = SharyMilk,
    logoPrimaryBar = lerp(SharyBlack, SharyMilk, 0.50f),
    logoSecondaryBar = SharyMilk,
    placeholderCoverFill = Color.Black.copy(alpha = 0.56f),
    placeholderLogoPrimary = SharyMilk.copy(alpha = 0.36f),
    placeholderLogoSecondary = SharyMilk.copy(alpha = 0.68f),
    songRowFill = Color.Black.copy(alpha = 0.48f),
    songRowActiveFill = SharyMilk.copy(alpha = 0.14f),
    songRowBorder = SharyMilk.copy(alpha = 0.11f),
    recentChipFill = Color.Black.copy(alpha = 0.54f),
    textPrimary = SharyMilk,
    textSecondary = SharyMilk.copy(alpha = 0.62f),
    heartActive = SharyMilk,
    dialogFill = lerp(SharyBlack, Color.Black, 0.42f).copy(alpha = 0.98f),
    dialogBorder = SharyMilk.copy(alpha = 0.20f),
    fieldFill = Color.Black.copy(alpha = 0.62f),
    fieldBorder = SharyMilk.copy(alpha = 0.16f),
    playerAccent = lerp(SharyBlack, SharyMilk, 0.24f),
    onAccent = SharyMilk,
    playerCenterGlow = SharyMilk.copy(alpha = 0.18f),
    playerTrackInactive = SharyMilk.copy(alpha = 0.20f),
    vinylDiscSurface = lerp(SharyBlack, Color.Black, 0.64f),
    vinylGrooveStroke = SharyMilk.copy(alpha = 0.34f),
    vibeDeepNebula = lerp(SharyBlack, SharyMilk, 0.06f),
    vibeDeepAura = lerp(SharyBlack, SharyMilk, 0.11f),
    vibeRayGlow = lerp(SharyBlack, SharyMilk, 0.26f),
    vibeMidSilk = lerp(SharyBlack, SharyMilk, 0.32f),
    vibeBrightCrest = SharyMilk.copy(alpha = 0.88f)
)

// 2. Графитовая / Серая тема («та, что сейчас»)
val DarkMilkBlackSurfacePalette = SharySurfacePalette(
    isLight = false,
    glassCloudAlpha = 0.88f,
    backgroundBase = SharyBlack,
    barFill = lerp(SharyBlack, SharyMilk, 0.16f).copy(alpha = 0.54f),
    barBorder = SharyMilk.copy(alpha = 0.13f),
    middleFill = lerp(SharyBlack, SharyMilk, 0.20f).copy(alpha = 0.56f),
    middleBorder = SharyMilk.copy(alpha = 0.14f),
    milkShimmerSpot = SharyMilk.copy(alpha = 0.055f),
    navIconInactive = SharyMilk.copy(alpha = 0.56f),
    navIconSelected = SharyMilk,
    navSelectionCircleSelected = SharyMilk.copy(alpha = 0.18f),
    navSelectionCirclePressed = SharyMilk.copy(alpha = 0.11f),
    transportPrimaryIcon = SharyMilk,
    transportSecondaryIcon = SharyMilk.copy(alpha = 0.50f),
    addCircleContainer = lerp(SharyBlack, SharyMilk, 0.34f),
    addCircleContainerPressed = lerp(SharyBlack, SharyMilk, 0.46f),
    addCircleIcon = SharyMilk,
    logoPrimaryBar = lerp(SharyBlack, SharyMilk, 0.54f),
    logoSecondaryBar = SharyMilk,
    placeholderCoverFill = lerp(SharyBlack, SharyMilk, 0.08f).copy(alpha = 0.72f),
    placeholderLogoPrimary = SharyMilk.copy(alpha = 0.38f),
    placeholderLogoSecondary = SharyMilk.copy(alpha = 0.68f),
    songRowFill = SharyBlack.copy(alpha = 0.42f),
    songRowActiveFill = SharyMilk.copy(alpha = 0.16f),
    songRowBorder = SharyMilk.copy(alpha = 0.10f),
    recentChipFill = SharyBlack.copy(alpha = 0.46f),
    textPrimary = SharyMilk,
    textSecondary = SharyMilk.copy(alpha = 0.60f),
    heartActive = SharyMilk,
    dialogFill = lerp(SharyBlack, SharyMilk, 0.08f).copy(alpha = 0.97f),
    dialogBorder = SharyMilk.copy(alpha = 0.20f),
    fieldFill = SharyBlack.copy(alpha = 0.58f),
    fieldBorder = SharyMilk.copy(alpha = 0.16f),
    playerAccent = lerp(SharyBlack, SharyMilk, 0.38f),
    onAccent = SharyMilk,
    playerCenterGlow = SharyMilk.copy(alpha = 0.22f),
    playerTrackInactive = SharyMilk.copy(alpha = 0.20f),
    vinylDiscSurface = lerp(SharyBlack, Color.Black, 0.48f),
    vinylGrooveStroke = SharyMilk.copy(alpha = 0.34f),
    vibeDeepNebula = lerp(SharyBlack, SharyMilk, 0.14f),
    vibeDeepAura = lerp(SharyBlack, SharyMilk, 0.22f),
    vibeRayGlow = lerp(SharyBlack, SharyMilk, 0.44f),
    vibeMidSilk = lerp(SharyBlack, SharyMilk, 0.52f),
    vibeBrightCrest = SharyMilk
)

// 3. Светлая тема (чистый Milk #FFFDF4 + чёткая графика Black #222323)
val LightMilkBlackSurfacePalette = SharySurfacePalette(
    isLight = true,
    glassCloudAlpha = 0.36f,
    backgroundBase = SharyMilk,
    barFill = lerp(SharyMilk, SharyBlack, 0.07f).copy(alpha = 0.78f),
    barBorder = SharyBlack.copy(alpha = 0.15f),
    middleFill = lerp(SharyMilk, SharyBlack, 0.06f).copy(alpha = 0.80f),
    middleBorder = SharyBlack.copy(alpha = 0.14f),
    milkShimmerSpot = SharyMilk.copy(alpha = 0.34f),
    navIconInactive = SharyBlack.copy(alpha = 0.52f),
    navIconSelected = SharyBlack,
    navSelectionCircleSelected = SharyBlack.copy(alpha = 0.13f),
    navSelectionCirclePressed = SharyBlack.copy(alpha = 0.08f),
    transportPrimaryIcon = SharyBlack,
    transportSecondaryIcon = SharyBlack.copy(alpha = 0.46f),
    addCircleContainer = SharyBlack,
    addCircleContainerPressed = lerp(SharyBlack, SharyMilk, 0.22f),
    addCircleIcon = SharyMilk,
    logoPrimaryBar = lerp(SharyBlack, SharyMilk, 0.42f),
    logoSecondaryBar = SharyBlack,
    placeholderCoverFill = SharyBlack.copy(alpha = 0.09f),
    placeholderLogoPrimary = SharyBlack.copy(alpha = 0.36f),
    placeholderLogoSecondary = SharyBlack.copy(alpha = 0.65f),
    songRowFill = SharyBlack.copy(alpha = 0.06f),
    songRowActiveFill = SharyBlack.copy(alpha = 0.13f),
    songRowBorder = SharyBlack.copy(alpha = 0.11f),
    recentChipFill = SharyBlack.copy(alpha = 0.08f),
    textPrimary = SharyBlack,
    textSecondary = SharyBlack.copy(alpha = 0.60f),
    heartActive = SharyBlack,
    dialogFill = lerp(SharyMilk, SharyBlack, 0.04f).copy(alpha = 0.98f),
    dialogBorder = SharyBlack.copy(alpha = 0.18f),
    fieldFill = SharyBlack.copy(alpha = 0.06f),
    fieldBorder = SharyBlack.copy(alpha = 0.16f),
    playerAccent = SharyBlack,
    onAccent = SharyMilk,
    playerCenterGlow = SharyBlack.copy(alpha = 0.18f),
    playerTrackInactive = SharyBlack.copy(alpha = 0.20f),
    vinylDiscSurface = SharyBlack,
    vinylGrooveStroke = SharyMilk.copy(alpha = 0.38f),
    vibeDeepNebula = lerp(SharyMilk, SharyBlack, 0.10f),
    vibeDeepAura = lerp(SharyMilk, SharyBlack, 0.16f),
    vibeRayGlow = lerp(SharyMilk, SharyBlack, 0.26f),
    vibeMidSilk = lerp(SharyMilk, SharyBlack, 0.34f),
    vibeBrightCrest = SharyBlack.copy(alpha = 0.72f)
)

val LocalSharySurfacePalette = staticCompositionLocalOf {
    DarkMilkBlackSurfacePalette
}
