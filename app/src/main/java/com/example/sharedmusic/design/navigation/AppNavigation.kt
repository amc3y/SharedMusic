package com.example.sharedmusic.design.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sharedmusic.design.screens.HomeScreen
import com.example.sharedmusic.design.screens.LibraryScreen
import com.example.sharedmusic.design.screens.RoomScreen
import com.example.sharedmusic.logic.model.RemotePlaybackCommand
import com.example.sharedmusic.logic.room.RoomController
import com.example.sharedmusic.logic.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

@Composable
fun AppNavigation() {
    var currentScreen by remember {
        mutableStateOf("main")
    }

    val context = LocalContext.current

    val musicViewModel: MusicViewModel = viewModel()

    val localSongs by musicViewModel.songs.collectAsState()

    // Менеджер не пересоздаётся при переходе между экранами.
    val connectionManager = remember(context) {
        RoomController(context.applicationContext)
    }

    val remoteScope = rememberCoroutineScope()

    val latestRemoteCommand =
        remember { AtomicReference<RemotePlaybackCommand?>(null) }

    val pendingRemoteTracks =
        remember { ConcurrentHashMap.newKeySet<String>() }

    DisposableEffect(musicViewModel, connectionManager) {

        // Локальный Play / Pause / Seek отправляет состояние в Firebase.
        musicViewModel.setPlaybackPublisher {
                song, positionMs, isPlaying ->
            connectionManager.publishPlaybackState(
                song = song,
                positionMs = positionMs,
                isPlaying = isPlaying
            )
        }

        // Автопереход к следующему треку плейлиста комнаты
        musicViewModel.setOnTrackEnded { ended ->
            if (connectionManager.currentRoomCode == null) return@setOnTrackEnded
            if (!connectionManager.canControlPlayback()) return@setOnTrackEnded

            val localSongs = musicViewModel.songs.value
            val playlist = connectionManager.roomTracks.mapNotNull { track ->
                localSongs.firstOrNull {
                    it.id == track.id ||
                            it.title.trim().equals(
                                track.title.trim(),
                                ignoreCase = true
                            )
                }
            }
            if (playlist.isEmpty()) return@setOnTrackEnded

            val next = musicViewModel.nextSongAfter(ended.id, playlist)
                ?: return@setOnTrackEnded
            musicViewModel.playSong(next)
        }

        connectionManager.setPlaybackListener { command ->

            latestRemoteCommand.set(command)

            // Если песня уже есть на устройстве, применяем команду сразу.
            val applied = musicViewModel.applyRemotePlayback(command)

            if (!applied) {
                val trackId = command.trackId

                // Не запускаем несколько скачиваний одной и той же песни.
                if (pendingRemoteTracks.add(trackId)) {
                    remoteScope.launch {
                        try {
                            repeat(120) {
                                val latest = latestRemoteCommand.get()
                                    ?: return@launch

                                // За время ожидания могла прийти другая команда.
                                if (latest.trackId != trackId) {
                                    return@launch
                                }

                                if (
                                    !connectionManager
                                        .isLatestPlaybackCommand(latest)
                                ) {
                                    return@launch
                                }

                                if (
                                    musicViewModel.applyRemotePlayback(latest)
                                ) {
                                    return@launch
                                }

                                val track =
                                    connectionManager.roomTracks.firstOrNull {
                                        it.id == trackId
                                    }

                                // Пока менеджер выполняет другую операцию,
                                // ждём, а не начинаем вторую загрузку.
                                if (
                                    track != null &&
                                    !connectionManager.isBusy
                                ) {
                                    connectionManager.downloadTrack(track) { file ->

                                        musicViewModel.addSongFromFile(
                                            file = file,
                                            title = track.title,
                                            songId = track.id
                                        )

                                        val newest = latestRemoteCommand.get()

                                        // Применяем только самую свежую команду.
                                        if (
                                            newest != null &&
                                            newest.trackId == track.id &&
                                            connectionManager
                                                .isLatestPlaybackCommand(newest)
                                        ) {
                                            musicViewModel
                                                .applyRemotePlayback(newest)
                                        }
                                    }

                                    return@launch
                                }

                                // Даём Firebase время обновить список треков
                                // и ждём завершения текущей операции.
                                delay(250L)
                            }
                        } finally {
                            pendingRemoteTracks.remove(trackId)
                        }
                    }
                }
            }
        }

        onDispose {
            musicViewModel.setPlaybackPublisher(null)
            musicViewModel.setOnTrackEnded(null)
            connectionManager.setPlaybackListener(null)
            connectionManager.dispose()
        }
    }

    // Раз в 3 секунды отправляем контрольную позицию.
    // RoomController сам проверяет, какое устройство является ведущим.
    LaunchedEffect(connectionManager, musicViewModel) {
        while (true) {
            delay(3_000L)

            val song = musicViewModel.currentSong.value

            if (
                song != null &&
                musicViewModel.isPlaying.value
            ) {
                connectionManager.publishHeartbeat(
                    song = song,
                    positionMs = musicViewModel.positionMs.value
                )
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.weight(1f)
        ) {
            when (currentScreen) {
                "main" -> {
                    HomeScreen(
                        onOpenLibraryClick = {
                            currentScreen = "library"
                        },
                        onOpenConnectionClick = {
                            currentScreen = "connection"
                        }
                    )
                }

                "library" -> {
                    LibraryScreen(
                        viewModel = musicViewModel,
                        onBackClick = {
                            currentScreen = "main"
                        }
                    )
                }

                "connection" -> {
                    RoomScreen(
                        connectionManager = connectionManager,
                        localSongs = localSongs,
                        onAddSongFromFile = { file, songId, title ->
                            musicViewModel.addSongFromFile(
                                file = file,
                                title = title,
                                songId = songId
                            )
                        },
                        onPlayRoomTrack = { song ->
                            musicViewModel.playSong(song)
                        },
                        onBack = {
                            // Возвращаемся, не выходя из комнаты.
                            currentScreen = "main"
                        }
                    )
                }
            }
        }

        PlaybackControls(
            musicViewModel = musicViewModel
        )
    }
}

@Composable
private fun PlaybackControls(
    musicViewModel: MusicViewModel
) {
    val currentSong by musicViewModel.currentSong.collectAsState()
    val isPlaying by musicViewModel.isPlaying.collectAsState()
    val positionMs by musicViewModel.positionMs.collectAsState()
    val durationMs by musicViewModel.durationMs.collectAsState()

    var isSeeking by remember {
        mutableStateOf(false)
    }

    var sliderValue by remember(currentSong?.id) {
        mutableStateOf(positionMs.toFloat())
    }

    LaunchedEffect(positionMs, isSeeking) {
        if (!isSeeking) {
            sliderValue = positionMs.toFloat()
        }
    }

    val maxPosition = durationMs
        .coerceAtLeast(1L)
        .toFloat()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Divider()

        Text(
            text = currentSong?.title ?: "Песня не выбрана",
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1
        )

        Slider(
            value = sliderValue.coerceIn(0f, maxPosition),
            onValueChange = { newValue ->
                isSeeking = true
                sliderValue = newValue
            },
            onValueChangeFinished = {
                musicViewModel.seekTo(sliderValue.toLong())
                isSeeking = false
            },
            valueRange = 0f..maxPosition,
            enabled = currentSong != null && durationMs > 0L
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(formatPlaybackTime(positionMs))

            Spacer(modifier = Modifier.weight(1f))

            Button(
                enabled = currentSong != null,
                onClick = {
                    musicViewModel.toggleMusic()
                }
            ) {
                Text(
                    if (isPlaying) "Пауза"
                    else "Воспроизвести"
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(formatPlaybackTime(durationMs))
        }
    }
}

private fun formatPlaybackTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L

    return "%d:%02d".format(minutes, seconds)
}
