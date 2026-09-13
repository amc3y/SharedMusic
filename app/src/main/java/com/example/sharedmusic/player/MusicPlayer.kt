package com.example.sharedmusic.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

// Простой плеер на ExoPlayer. Экран работает с ним через эти методы.
class MusicPlayer(context: Context) {
    // applicationContext — чтобы не держать ссылку на экран (защита от утечек)
    private val player = ExoPlayer.Builder(context.applicationContext).build()
    private var currentUri: Uri? = null

    // Играть: если файл новый — загрузить, потом играть
    fun play(uri: Uri) {
        if (currentUri != uri) {
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            currentUri = uri
        }
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
        currentUri = null
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun positionMs(): Long = player.currentPosition.coerceAtLeast(0L)

    fun durationMs(): Long = player.duration.coerceAtLeast(0L)

    fun isPlayingNow(): Boolean = player.isPlaying

    fun release() {
        player.release()
    }
}