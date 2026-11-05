package com.example.sharedmusic.logic.model

// Роли в комнате: админ, младший админ, обычный участник
enum class RoomRole {
    ADMIN,
    MODERATOR,
    MEMBER;

    fun canModerate(): Boolean = this == ADMIN || this == MODERATOR

    companion object {
        fun fromString(value: String?): RoomRole = when (value?.uppercase()) {
            "ADMIN" -> ADMIN
            "MODERATOR" -> MODERATOR
            else -> MEMBER
        }
    }
}
