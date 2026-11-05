package com.example.sharedmusic.logic.model

// Участник комнаты
data class RoomDevice(
    val id: String,
    val name: String,
    val role: RoomRole = RoomRole.MEMBER
)
