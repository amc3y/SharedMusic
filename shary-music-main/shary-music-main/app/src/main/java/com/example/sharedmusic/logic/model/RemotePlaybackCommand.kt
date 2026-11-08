package com.example.sharedmusic.logic.model

// Команда синхронизации плеера из Firebase
data class RemotePlaybackCommand(
    val trackId: String,
    val title: String,
    val positionMs: Long,
    val isPlaying: Boolean,
    val updatedAt: Long,
    val updatedBy: String,
    val revision: String,
    val receivedAtMs: Long = System.currentTimeMillis()
)
