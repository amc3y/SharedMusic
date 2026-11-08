package com.example.sharedmusic.design.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.example.sharedmusic.logic.model.RemotePlaybackCommand
import com.example.sharedmusic.logic.model.Song
import com.example.sharedmusic.logic.room.RoomController
import com.example.sharedmusic.logic.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

// Единый мост синхронизации партнёра между локальным плеером (MusicViewModel) и комнатой (RoomController):
// - публикует Play/Pause/Seek в Firebase для админов;
// - автоматически скачивает во временный кэш и запускает удалённый трек у слушателей с учётом настроек «Только по Wi-Fi» и «Только безопасные MP3»;
// - управляет автопереходом треков как внутри Комнаты, так и в локальной Библиотеке.
@Composable
fun PartnerPlaybackBridge(
    musicViewModel: MusicViewModel,
    connectionManager: RoomController,
    userLibrarySongs: List<Song>,
    isRepeatEnabled: Boolean,
    isShuffleEnabled: Boolean,
    wifiOnlyTransfers: Boolean = false,
    onlySafeMp3Files: Boolean = false,
    onBlockedNotice: (String) -> Unit = {}
) {
    val appContext = LocalContext.current.applicationContext
    val remoteScope = rememberCoroutineScope()

    val latestLibrarySongs by rememberUpdatedState(userLibrarySongs)
    val latestRepeat by rememberUpdatedState(isRepeatEnabled)
    val latestShuffle by rememberUpdatedState(isShuffleEnabled)
    val latestWifiOnly by rememberUpdatedState(wifiOnlyTransfers)
    val latestSafeMp3Only by rememberUpdatedState(onlySafeMp3Files)
    val latestOnBlocked by rememberUpdatedState(onBlockedNotice)

    val latestRemoteCommand = remember {
        AtomicReference<RemotePlaybackCommand?>(null)
    }

    val pendingRemoteTracks = remember {
        ConcurrentHashMap.newKeySet<String>()
    }

    DisposableEffect(musicViewModel, connectionManager) {
        musicViewModel.setPlaybackPublisher { song, positionMs, isPlaying ->
            if (connectionManager.currentRoomCode != null && connectionManager.canControlPlayback()) {
                connectionManager.publishPlaybackState(
                    song = song,
                    positionMs = positionMs,
                    isPlaying = isPlaying
                )
            }
        }

        musicViewModel.setOnTrackEnded { ended ->
            if (connectionManager.currentRoomCode != null) {
                if (!connectionManager.canControlPlayback()) return@setOnTrackEnded

                val localSongs = musicViewModel.songs.value
                val playlist = connectionManager.roomTracks.mapNotNull { track ->
                    localSongs.firstOrNull {
                        it.id == track.id ||
                            it.title.trim().equals(track.title.trim(), ignoreCase = true)
                    }
                }
                if (playlist.isEmpty()) return@setOnTrackEnded

                when {
                    latestRepeat -> musicViewModel.playSong(ended)
                    latestShuffle && playlist.size > 1 -> {
                        val candidates = playlist.filter { it.id != ended.id }
                        musicViewModel.playSong(candidates.random())
                    }
                    else -> {
                        val next = musicViewModel.nextSongAfter(ended.id, playlist) ?: return@setOnTrackEnded
                        musicViewModel.playSong(next)
                    }
                }
            } else {
                val playlist = latestLibrarySongs.ifEmpty { musicViewModel.songs.value }
                if (playlist.isEmpty()) return@setOnTrackEnded

                when {
                    latestRepeat -> musicViewModel.playSong(ended)
                    latestShuffle && playlist.size > 1 -> {
                        val candidates = playlist.filter { it.id != ended.id }
                        musicViewModel.playSong(candidates.random())
                    }
                    else -> {
                        val next = musicViewModel.nextSongAfter(ended.id, playlist)
                        if (next != null) {
                            musicViewModel.playSong(next)
                        }
                    }
                }
            }
        }

        connectionManager.setPlaybackListener { command ->
            latestRemoteCommand.set(command)

            val applied = musicViewModel.applyRemotePlayback(command)
            if (!applied) {
                val trackId = command.trackId

                if (pendingRemoteTracks.add(trackId)) {
                    remoteScope.launch {
                        try {
                            if (latestWifiOnly && !AppSettingsStore.isWifiConnected(appContext)) {
                                latestOnBlocked("Ожидание Wi-Fi для загрузки трека комнаты")
                                return@launch
                            }

                            repeat(120) {
                                val latest = latestRemoteCommand.get()
                                    ?: return@launch

                                if (latest.trackId != trackId) {
                                    return@launch
                                }

                                if (!connectionManager.isLatestPlaybackCommand(latest)) {
                                    return@launch
                                }

                                if (musicViewModel.applyRemotePlayback(latest)) {
                                    return@launch
                                }

                                val track = connectionManager.roomTracks.firstOrNull {
                                    it.id == trackId ||
                                        it.title.trim().equals(latest.title.trim(), ignoreCase = true)
                                }

                                if (track != null && !connectionManager.isBusy) {
                                    if (latestSafeMp3Only && !AppSettingsStore.isSafeMp3RoomTrackMetadata(track)) {
                                        latestOnBlocked("Трек пропущен: разрешены только безопасные MP3")
                                        return@launch
                                    }

                                    connectionManager.downloadTrack(track) { file ->
                                        if (latestSafeMp3Only && !AppSettingsStore.isSafeMp3File(file)) {
                                            runCatching { file.delete() }
                                            latestOnBlocked("Файл отклонён проверкой безопасности MP3")
                                            return@downloadTrack
                                        }

                                        musicViewModel.addSongFromFile(
                                            file = file,
                                            title = track.title,
                                            songId = track.id
                                        )

                                        val newest = latestRemoteCommand.get()
                                        if (
                                            newest != null &&
                                            newest.trackId == track.id &&
                                            connectionManager.isLatestPlaybackCommand(newest)
                                        ) {
                                            musicViewModel.applyRemotePlayback(newest)
                                        }
                                    }
                                    return@launch
                                }

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

    LaunchedEffect(connectionManager, musicViewModel) {
        while (true) {
            delay(3_000L)
            val song = musicViewModel.currentSong.value
            if (
                song != null &&
                musicViewModel.isPlaying.value &&
                connectionManager.currentRoomCode != null &&
                connectionManager.canControlPlayback()
            ) {
                connectionManager.publishHeartbeat(
                    song = song,
                    positionMs = musicViewModel.positionMs.value
                )
            }
        }
    }
}
