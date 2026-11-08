package com.example.sharedmusic.logic.model

// Трек в плейлисте комнаты (ссылка в Supabase + id передачи)
data class RoomTrack(
    val id: String,
    val title: String,
    val downloadUrl: String,
    val addedBy: String,
    val fileName: String = "",
    val transferId: String = "",
    val objectPath: String = ""
)
