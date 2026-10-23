package com.example.sharedmusic.model

import android.net.Uri

data class Song(
    val id: String,
    val title: String,
    val uri: Uri,
    val durationMs: Long = 0L,
    val isRemote: Boolean = false
)