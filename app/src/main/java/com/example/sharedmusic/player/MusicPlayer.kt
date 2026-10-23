package com.example.sharedmusic.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class MusicPlayer(context: Context) {

    private val player = ExoPlayer.Builder(
        context.applicationContext
    ).build()

    val currentPositionMs: Long
        get() = player.currentPosition.coerceAtLeast(0L)

    val durationMs: Long
        get() {
            val duration = player.duration
            return if (duration == C.TIME_UNSET || duration < 0L) {
                0L
            } else {
                duration
            }
        }

    val isPlaying: Boolean
        get() = player.playWhenReady &&
                player.playbackState != Player.STATE_IDLE &&
                player.playbackState != Player.STATE_ENDED

    val hasEnded: Boolean
        get() = player.playbackState == Player.STATE_ENDED

    fun isCurrentUri(uri: Uri): Boolean {
        return player.currentMediaItem
            ?.localConfiguration
            ?.uri == uri
    }

    fun play(uri: Uri, startPositionMs: Long = 0L) {
        if (!isCurrentUri(uri)) {
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
        }

        player.seekTo(startPositionMs.coerceAtLeast(0L))
        player.play()
    }

    fun preparePaused(uri: Uri, positionMs: Long = 0L) {
        if (!isCurrentUri(uri)) {
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
        }

        player.pause()
        player.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun pause() {
        player.pause()
    }

    fun resume() {
        if (player.currentMediaItem == null) return

        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0L)
        }

        player.play()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun stop() {
        player.pause()
        player.seekTo(0L)
    }

    fun release() {
        player.release()
    }
}