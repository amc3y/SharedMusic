package com.example.sharedmusic.connection

/**
 * Роли в комнате.
 * ADMIN — создатель / текущий владелец
 * MODERATOR — младший админ (назначается админом)
 * MEMBER — обычный участник
 */
enum class RoomRole {
    ADMIN,
    MODERATOR,
    MEMBER;

    fun canModerate(): Boolean = this == ADMIN || this == MODERATOR

    companion object {
        fun fromString(value: String?): RoomRole {
            return when (value?.uppercase()) {
                "ADMIN" -> ADMIN
                "MODERATOR" -> MODERATOR
                else -> MEMBER
            }
        }
    }
}

/**
 * Режим прав комнаты.
 * OPEN — все могут добавлять/управлять музыкой
 * ADMIN_ONLY — только админ и младшие админы
 */
enum class RoomPermissionMode {
    OPEN,
    ADMIN_ONLY;

    companion object {
        fun fromString(value: String?): RoomPermissionMode {
            return when (value?.lowercase()) {
                "admin_only", "admin-only", "restricted" -> ADMIN_ONLY
                else -> OPEN
            }
        }
    }
}

data class RoomDevice(
    val id: String,
    val name: String,
    val role: RoomRole = RoomRole.MEMBER
)
