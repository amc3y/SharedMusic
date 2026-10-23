package com.example.sharedmusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.sharedmusic.connection.ConnectionManager
import com.example.sharedmusic.model.Song
import java.io.File

@Composable
fun ConnectionScreen(
    connectionManager: ConnectionManager,
    localSongs: List<Song>,
    onAddSongFromFile: (File, String, String) -> Unit,
    onBack: () -> Unit
) {
    var roomCodeInput by remember {
        mutableStateOf("")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                enabled = !connectionManager.isBusy,
                onClick = onBack
            ) {
                Text("Назад")
            }

            Spacer(modifier = Modifier.weight(1f))

            if (connectionManager.currentRoomCode != null) {
                OutlinedButton(
                    enabled = !connectionManager.isBusy,
                    onClick = {
                        connectionManager.leaveRoom()
                    }
                ) {
                    Text("Выйти из комнаты")
                }
            }
        }

        Text("SharedMusic — подключение")

        Divider()

        if (connectionManager.currentRoomCode == null) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !connectionManager.isBusy,
                onClick = {
                    connectionManager.createRoom()
                }
            ) {
                Text("Создать комнату")
            }

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = roomCodeInput,
                onValueChange = { value ->
                    roomCodeInput = value
                        .uppercase()
                        .filter {
                            it in "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
                        }
                        .take(4)
                },
                label = {
                    Text("Код комнаты")
                },
                singleLine = true,
                enabled = !connectionManager.isBusy
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !connectionManager.isBusy &&
                        roomCodeInput.length == 4,
                onClick = {
                    connectionManager.joinRoom(roomCodeInput)
                }
            ) {
                Text("Войти в комнату")
            }
        } else {
            Text("Код комнаты: ${connectionManager.currentRoomCode}")
        }

        Text("Статус: ${connectionManager.statusMessage}")

        if (connectionManager.isBusy) {
            if (connectionManager.progressText.isNotBlank()) {
                Text(connectionManager.progressText)
            }

            LinearProgressIndicator(
                progress = connectionManager.progress,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (connectionManager.currentRoomCode != null) {
            Divider()

            Text("Подключённые устройства")

            if (connectionManager.connectedDevices.isEmpty()) {
                Text("Пока нет подключённых устройств.")
            } else {
                connectionManager.connectedDevices.forEach { device ->
                    Text("• ${device.name}")
                }
            }

            Divider()

            Text("Музыка в комнате")

            if (connectionManager.roomTracks.isEmpty()) {
                Text("В комнате пока нет песен.")
            } else {
                connectionManager.roomTracks.forEach { track ->
                    val alreadyExists = localSongs.any { localSong ->
                        localSong.id == track.id ||
                                localSong.title.trim().equals(
                                    track.title.trim(),
                                    ignoreCase = true
                                )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(track.title)

                            if (alreadyExists) {
                                Text("Уже есть на устройстве")
                            }
                        }

                        if (!alreadyExists) {
                            Button(
                                enabled = !connectionManager.isBusy,
                                onClick = {
                                    connectionManager.downloadTrack(track) { file ->
                                        onAddSongFromFile(
                                            file,
                                            track.id,
                                            track.title
                                        )
                                    }
                                }
                            ) {
                                Text("Скачать")
                            }
                        }
                    }
                }
            }

            Divider()

            Text("Мои песни")

            if (localSongs.isEmpty()) {
                Text("Сначала добавьте музыку в локальный плеер.")
            } else {
                localSongs.forEach { song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = song.title,
                            modifier = Modifier.weight(1f)
                        )

                        Button(
                            enabled = !connectionManager.isBusy,
                            onClick = {
                                connectionManager.shareSong(song)
                            }
                        ) {
                            Text("Отправить")
                        }
                    }
                }
            }
        }
    }
}