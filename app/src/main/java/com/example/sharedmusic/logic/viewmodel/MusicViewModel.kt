package com.example.sharedmusic.logic.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import com.example.sharedmusic.logic.model.RemotePlaybackCommand
import com.example.sharedmusic.logic.library.SongLibrary
import com.example.sharedmusic.logic.model.Song
import com.example.sharedmusic.logic.player.MusicPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.abs

class MusicViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val musicPlayer = MusicPlayer(application)
    private val songLibrary = SongLibrary(application)

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

    private var onTrackEnded: ((Song) -> Unit)? = null
    private var endEventConsumed = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private val playbackUpdater = object : Runnable {
        override fun run() {
            _positionMs.value = musicPlayer.currentPositionMs
            _durationMs.value = musicPlayer.durationMs

            val actualIsPlaying = musicPlayer.isPlaying

            if (musicPlayer.hasEnded) {
                if (_isPlaying.value || !endEventConsumed) {
                    val ended = _currentSong.value
                    _isPlaying.value = false
                    if (!endEventConsumed && ended != null) {
                        endEventConsumed = true
                        publishCurrentPlayback(ended)
                        onTrackEnded?.invoke(ended)
                    }
                }
            } else {
                endEventConsumed = false
                _isPlaying.value = actualIsPlaying
            }

            mainHandler.postDelayed(this, 250L)
        }
    }

    init {
        // Восстанавливаем сохранённую библиотеку
        _songs.value = songLibrary.loadSongs()
        mainHandler.post(playbackUpdater)
    }

    fun setPlaybackPublisher(
        publisher: ((Song, Long, Boolean) -> Unit)?
    ) {
        playbackPublisher = publisher
    }

    fun setOnTrackEnded(listener: ((Song) -> Unit)?) {
        onTrackEnded = listener
    }

    fun addSong(uri: Uri) {
        addSongs(listOf(uri))
    }

    fun addSongs(uris: List<Uri>) {
        if (uris.isEmpty()) return

        val current = _songs.value.toMutableList()
        var changed = false

        for (uri in uris) {
            try {
                val song = songLibrary.importFromUri(uri)
                if (current.any { it.id == song.id }) continue
                val path = song.uri.path
                if (path != null && current.any { it.uri.path == path }) {
                    continue
                }
                current.add(song)
                changed = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (changed) {
            _songs.value = current.toList()
            songLibrary.saveSongs(_songs.value)
        }
    }

    fun addSongFromFile(
        file: File,
        title: String,
        songId: String? = null
    ) {
        if (!file.exists() || !file.isFile) return

        val finalId = songId ?: java.util.UUID.randomUUID().toString()

        if (_songs.value.any { it.id == finalId }) return

        try {
            val song = songLibrary.importFromFile(
                source = file,
                title = title,
                songId = finalId,
                isRemote = true
            )

            val updated = _songs.value + song
            _songs.value = updated
            songLibrary.saveSongs(updated)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun removeSong(song: Song) {
        if (_currentSong.value?.id == song.id) {
            musicPlayer.stop()
            _currentSong.value = null
            _isPlaying.value = false
            _positionMs.value = 0L
        }

        val updated = _songs.value.filterNot { it.id == song.id }
        _songs.value = updated
        songLibrary.deleteSongFiles(song)
        songLibrary.saveSongs(updated)
    }

    fun playSong(song: Song) {
        // Файл мог быть удалён с диска
        val path = song.uri.path
        if (path != null) {
            val f = File(path)
            if (!f.exists()) {
                removeSong(song)
                return
            }
        }

        _currentSong.value = song
        endEventConsumed = false

        musicPlayer.play(
            uri = song.uri,
            startPositionMs = 0L
        )

        _isPlaying.value = true
        _positionMs.value = 0L

        publishCurrentPlayback()
    }

    fun nextSongAfter(currentId: String, playlist: List<Song>): Song? {
        if (playlist.isEmpty()) return null
        val index = playlist.indexOfFirst { it.id == currentId }
        if (index < 0) return playlist.first()
        return playlist[(index + 1) % playlist.size]
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

        val song = if (candidate.id != command.trackId) {
            val updatedSong = candidate.copy(id = command.trackId)

            val mapped = _songs.value.map {
                if (it.id == candidate.id) updatedSong else it
            }
            _songs.value = mapped
            songLibrary.saveSongs(mapped)

            updatedSong
        } else {
            candidate
        }

        _currentSong.value = song

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
        onTrackEnded = null

        musicPlayer.release()

        super.onCleared()
    }
}
