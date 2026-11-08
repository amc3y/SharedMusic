package com.example.sharedmusic.logic.model

// Режим прав: все управляют музыкой или только админы
enum class RoomPermissionMode {
    OPEN,
    ADMIN_ONLY;

    companion object {
        fun fromString(value: String?): RoomPermissionMode = when (value?.lowercase()) {
            "admin_only", "admin-only", "restricted" -> ADMIN_ONLY
            else -> OPEN
        }
    }
}
