package com.example.sharedmusic.design.navigation

import androidx.annotation.StringRes
import com.example.sharedmusic.R

// Четыре основных раздела нижней панели навигации SHARY
enum class SharyTab(
    @StringRes val labelResId: Int
) {
    LIBRARY(R.string.nav_library),
    ROOM(R.string.nav_room),
    SETTINGS(R.string.nav_settings),
    PROFILE(R.string.nav_profile)
}
