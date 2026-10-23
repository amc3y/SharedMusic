package com.example.sharedmusic.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import com.example.sharedmusic.connection.RemotePlaybackCommand
import com.example.sharedmusic.model.Song
import com.example.sharedmusic.player.MusicPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID
import kotlin.math.abs

class MusicViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val musicPlayer = MusicPlayer(application)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs = _songs.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs = _durationMs.asStateFlow()

    private var playbackPublisher:
            ((Song, Long, Boolean) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private val playbackUpdater = object : Runnable {
        override fun run() {
            _positionMs.value = musicPlayer.currentPositionMs
            _durationMs.value = musicPlayer.durationMs

            val actualIsPlaying = musicPlayer.isPlaying

            // Если песня сама закончилась, сообщаем об этом комнате.
            if (_isPlaying.value && musicPlayer.hasEnded) {
                _isPlaying.value = false
                publishCurrentPlayback()
            } else {
                _isPlaying.value = actualIsPlaying
            }

            mainHandler.postDelayed(this, 250L)
        }
    }

    init {
        mainHandler.post(playbackUpdater)
    }

    fun setPlaybackPublisher(
        publisher: ((Song, Long, Boolean) -> Unit)?
    ) {
        playbackPublisher = publisher
    }

    fun addSong(uri: Uri) {
        val title = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?: "Неизвестная песня"

        val song = Song(
            id = UUID.randomUUID().toString(),
            title = title,
            uri = uri
        )

        _songs.value = _songs.value + song
    }

    fun addSongFromFile(
        file: File,
        title: String,
        songId: String? = null
    ) {
        if (!file.exists() || !file.isFile) return

        val finalId = songId ?: UUID.randomUUID().toString()

        // Не добавляем один и тот же общий трек повторно.
        if (_songs.value.any { it.id == finalId }) return

        val song = Song(
            id = finalId,
            title = title,
            uri = Uri.fromFile(file),
            isRemote = true
        )

        _songs.value = _songs.value + song
    }

    fun playSong(song: Song) {
        _currentSong.value = song

        musicPlayer.play(
            uri = song.uri,
            startPositionMs = 0L
        )

        _isPlaying.value = true
        _positionMs.value = 0L

        publishCurrentPlayback()
    }

    fun toggleMusic() {
        val song = _currentSong.value ?: return

        if (_isPlaying.value) {
            musicPlayer.pause()
            _isPlaying.value = false
        } else {
            musicPlayer.resume()
            _isPlaying.value = musicPlayer.isPlaying
        }

        _positionMs.value = musicPlayer.currentPositionMs
        publishCurrentPlayback(song)
    }

    fun stopMusic() {
        val song = _currentSong.value ?: return

        musicPlayer.stop()

        _positionMs.value = 0L
        _isPlaying.value = false

        publishCurrentPlayback(song)
    }

    fun seekTo(positionMs: Long) {
        val song = _currentSong.value ?: return

        val duration = musicPlayer.durationMs

        val targetPosition = if (duration > 0L) {
            positionMs.coerceIn(0L, duration)
        } else {
            positionMs.coerceAtLeast(0L)
        }

        musicPlayer.seekTo(targetPosition)
        _positionMs.value = targetPosition

        publishCurrentPlayback(song)
    }

    /**
     * Возвращает false, если соответствующей песни ещё нет локально.
     * Удалённые команды не публикуются повторно.
     */
    fun applyRemotePlayback(
        command: RemotePlaybackCommand
    ): Boolean {
        val candidate = _songs.value.firstOrNull {
            it.id == command.trackId
        } ?: _songs.value.firstOrNull {
            it.title.trim().equals(
                command.title.trim(),
                ignoreCase = true
            )
        } ?: return false

        val previousSong = _currentSong.value

        // Используем одинаковый ID общего трека на обоих устройствах.
        val song = if (candidate.id != command.trackId) {
            val updatedSong = candidate.copy(id = command.trackId)

            _songs.value = _songs.value.map {
                if (it.id == candidate.id) updatedSong else it
            }

            updatedSong
        } else {
            candidate
        }

        _currentSong.value = song

        // Учитываем время, прошедшее с получения команды,
        // например пока скачивался файл.
        val elapsedMs = if (command.isPlaying) {
            (System.currentTimeMillis() - command.receivedAtMs)
                .coerceAtLeast(0L)
        } else {
            0L
        }

        var targetPosition = command.positionMs + elapsedMs

        if (song.durationMs > 0L) {
            targetPosition = targetPosition.coerceIn(
                0L,
                song.durationMs
            )
        } else {
            targetPosition = targetPosition.coerceAtLeast(0L)
        }

        val sameMedia = previousSong?.uri == song.uri &&
                musicPlayer.isCurrentUri(song.uri)

        if (!sameMedia) {
            if (command.isPlaying) {
                musicPlayer.play(
                    uri = song.uri,
                    startPositionMs = targetPosition
                )
            } else {
                musicPlayer.preparePaused(
                    uri = song.uri,
                    positionMs = targetPosition
                )
            }
        } else {
            val difference = abs(
                musicPlayer.currentPositionMs - targetPosition
            )

            if (command.isPlaying) {
                // Не перематываем при каждом небольшом расхождении.
                if (difference > 1_500L) {
                    musicPlayer.seekTo(targetPosition)
                }

                musicPlayer.resume()
            } else {
                musicPlayer.pause()

                if (difference > 500L) {
                    musicPlayer.seekTo(targetPosition)
                }
            }
        }

        _positionMs.value = targetPosition
        _isPlaying.value = command.isPlaying

        return true
    }

    private fun publishCurrentPlayback(
        song: Song? = _currentSong.value
    ) {
        val current = song ?: return

        playbackPublisher?.invoke(
            current,
            musicPlayer.currentPositionMs,
            musicPlayer.isPlaying
        )
    }

    override fun onCleared() {
        mainHandler.removeCallbacks(playbackUpdater)
        playbackPublisher = null

        musicPlayer.release()

        super.onCleared()
    }
}