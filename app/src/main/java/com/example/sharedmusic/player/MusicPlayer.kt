package com.example.sharedmusic.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

// Чисто музыка
class MusicPlayer(context: Context){
    private val player = ExoPlayer.Builder(context).build()

    fun play(uri: Uri) {
        // Берём музыку
        val mediaItem = MediaItem.fromUri(uri)
        // Закидываем, как то что нужно воспроизвести
        player.setMediaItem(mediaItem)
        //Подготавливаем файл
        player.prepare()
        //Воспроизводим
        player.play()
    }

    fun stop() {
        player.stop()
    }

    fun release() {

        player.release()
    }
}