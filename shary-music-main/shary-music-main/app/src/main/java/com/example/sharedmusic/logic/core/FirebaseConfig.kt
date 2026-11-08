package com.example.sharedmusic.logic.core

// Настройки Firebase Realtime Database и лимиты комнаты
object FirebaseConfig {
    const val DATABASE_URL =
        "https://sharedmusic-38102-default-rtdb.europe-west1.firebasedatabase.app"

    const val ROOM_CODE_LENGTH = 6
    const val ROOM_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    const val MIN_CAPACITY = 2
    const val MAX_CAPACITY = 20
    const val DEFAULT_CAPACITY = 5
}
