package com.example.sharedmusic.design.screens

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
import androidx.compose.ui.unit.dp
import com.example.sharedmusic.logic.room.RoomController
import com.example.sharedmusic.logic.model.RoomPermissionMode
import com.example.sharedmusic.logic.model.RoomRole
import com.example.sharedmusic.logic.model.Song
import java.io.File

@Composable
fun RoomScreen(
    connectionManager: RoomController,
    localSongs: List<Song>,
    onAddSongFromFile: (File, String, String) -> Unit,
    onPlayRoomTrack: (Song) -> Unit,
    onBack: () -> Unit
) {
    var roomCodeInput by remember { mutableStateOf("") }
    var capacityInput by remember {
        mutableStateOf(RoomController.DEFAULT_CAPACITY.toString())
    }
    var passwordInput by remember { mutableStateOf("") }
    var joinPasswordInput by remember { mutableStateOf("") }
    var adminOnlyMode by remember { mutableStateOf(false) }

    // id локальных песен, которые засеем при создании комнаты
    var selectedSeedIds by remember { mutableStateOf(setOf<String>()) }
    // id для доп. отправки в уже созданную комнату
    var selectedShareIds by remember { mutableStateOf(setOf<String>()) }

    val inRoom = connectionManager.currentRoomCode != null
    val missing = remember(localSongs, connectionManager.roomTracks.toList()) {
        connectionManager.missingRoomTracks(localSongs)
    }
    val libraryReady = missing.isEmpty()

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
            ) { Text("Назад") }

            Spacer(modifier = Modifier.weight(1f))

            if (inRoom) {
                OutlinedButton(
                    enabled = !connectionManager.isBusy,
                    onClick = { connectionManager.leaveRoom() }
                ) { Text("Выйти из комнаты") }
            }
        }

        Text("SharedMusic — комната")
        Divider()

        if (!inRoom) {
            Text("Создание комнаты")

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = capacityInput,
                onValueChange = {
                    capacityInput = it.filter { c -> c.isDigit() }.take(2)
                },
                label = {
                    Text(
                        "Вместимость (${RoomController.MIN_CAPACITY}–" +
                                "${RoomController.MAX_CAPACITY})"
                    )
                },
                singleLine = true,
                enabled = !connectionManager.isBusy
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = passwordInput,
                onValueChange = { passwordInput = it.take(32) },
                label = { Text("Пароль (необязательно)") },
                singleLine = true,
                enabled = !connectionManager.isBusy
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = !connectionManager.isBusy,
                    onClick = { adminOnlyMode = false }
                ) {
                    Text(if (!adminOnlyMode) "● Права у всех" else "Права у всех")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = !connectionManager.isBusy,
                    onClick = { adminOnlyMode = true }
                ) {
                    Text(if (adminOnlyMode) "● Только админы" else "Только админы")
                }
            }

            Text("Музыка комнаты (гости скачают при входе):")
            if (localSongs.isEmpty()) {
                Text("Сначала добавьте песни на главном экране.")
            } else {
                localSongs.forEach { song ->
                    val selected = song.id in selectedSeedIds
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !connectionManager.isBusy,
                        onClick = {
                            selectedSeedIds = if (selected) {
                                selectedSeedIds - song.id
                            } else {
                                selectedSeedIds + song.id
                            }
                        }
                    ) {
                        Text(
                            (if (selected) "✓ " else "○ ") + song.title
                        )
                    }
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !connectionManager.isBusy,
                onClick = {
                    val capacity = capacityInput.toIntOrNull()
                        ?: RoomController.DEFAULT_CAPACITY
                    val seed = localSongs.filter { it.id in selectedSeedIds }

                    connectionManager.createRoom(
                        maxCapacity = capacity,
                        password = passwordInput,
                        permissionMode = if (adminOnlyMode) {
                            RoomPermissionMode.ADMIN_ONLY
                        } else {
                            RoomPermissionMode.OPEN
                        }
                    )

                    // После создания roomCode появится асинхронно —
                    // отправку сидим через небольшой повтор в onFinished-пути:
                    // пользователь нажмёт «Отправить выбранные» или мы
                    // планируем отправку, когда комната станет активной.
                    // Здесь: если seed не пуст, после createRoom isBusy
                    // сбросится — пользователь увидит кнопку ниже.
                    // Автозагрузка сида: RoomController.createRoom
                    // не принимает songs, поэтому делаем отложенную отправку
                    // через remember flag.
                    if (seed.isNotEmpty()) {
                        // флаг обработаем блоком ниже через pendingSeed
                    }
                }
            ) {
                Text(
                    if (selectedSeedIds.isEmpty()) {
                        "Создать комнату (без треков)"
                    } else {
                        "Создать комнату + ${selectedSeedIds.size} трек(ов)"
                    }
                )
            }

            // Отложенная отправка сида после появления кода комнаты
            PendingSeedUploader(
                connectionManager = connectionManager,
                seedSongs = localSongs.filter { it.id in selectedSeedIds }
            )

            Divider()
            Text("Вход в комнату")

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = roomCodeInput,
                onValueChange = { value ->
                    roomCodeInput = value.uppercase().filter {
                        it in "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
                    }.take(4)
                },
                label = { Text("Код комнаты") },
                singleLine = true,
                enabled = !connectionManager.isBusy
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = joinPasswordInput,
                onValueChange = { joinPasswordInput = it.take(32) },
                label = { Text("Пароль (если есть)") },
                singleLine = true,
                enabled = !connectionManager.isBusy
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !connectionManager.isBusy &&
                        roomCodeInput.length == 4,
                onClick = {
                    connectionManager.joinRoom(
                        roomCodeInput,
                        joinPasswordInput
                    )
                }
            ) { Text("Войти в комнату") }
        } else {
            // === В комнате ===
            Text("Код: ${connectionManager.currentRoomCode}")
            Text(
                "Участники: ${connectionManager.connectedDevices.size} / " +
                        "${connectionManager.roomCapacity}"
            )
            Text(
                "Режим: " +
                        if (connectionManager.permissionMode ==
                            RoomPermissionMode.ADMIN_ONLY
                        ) "только админы" else "права у всех"
            )
            Text("Роль: ${roleLabel(connectionManager.myRole)}")

            // Обязательная библиотека
            if (!libraryReady) {
                Divider()
                Text(
                    "Чтобы остаться в комнате, скачайте всю музыку " +
                            "(${missing.size} шт.):"
                )
                missing.forEach { track ->
                    Text("• ${track.title}")
                }

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !connectionManager.isBusy,
                    onClick = {
                        connectionManager.downloadMissingTracks(
                            localSongs = localSongs,
                            onEachReady = { file, track ->
                                onAddSongFromFile(
                                    file,
                                    track.id,
                                    track.title
                                )
                            },
                            onFinished = { _, _ -> }
                        )
                    }
                ) {
                    Text("Скачать всё")
                }

                Text(
                    "Пока не скачаете все треки, управление и " +
                            "совместное прослушивание недоступны."
                )
            } else {
                Text("Библиотека комнаты: готова ✓")
            }
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

        if (inRoom && libraryReady) {
            Divider()
            Text("Участники")
            connectionManager.connectedDevices.forEach { device ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "• ${device.name} (${roleLabel(device.role)})",
                        modifier = Modifier.weight(1f)
                    )
                    if (
                        connectionManager.canManageRoles() &&
                        device.role == RoomRole.MEMBER
                    ) {
                        OutlinedButton(
                            enabled = !connectionManager.isBusy,
                            onClick = {
                                connectionManager.promoteToModerator(device.id)
                            }
                        ) { Text("Мл. админ") }
                    }
                    if (
                        connectionManager.canManageRoles() &&
                        device.role == RoomRole.MODERATOR
                    ) {
                        OutlinedButton(
                            enabled = !connectionManager.isBusy,
                            onClick = {
                                connectionManager.demoteToMember(device.id)
                            }
                        ) { Text("Снять") }
                    }
                }
            }

            Divider()
            Text("Плейлист комнаты (по порядку)")

            if (connectionManager.roomTracks.isEmpty()) {
                Text("В комнате пока нет песен.")
            } else {
                connectionManager.roomTracks.forEachIndexed { index, track ->
                    val local = localSongs.firstOrNull {
                        it.id == track.id ||
                                it.title.trim().equals(
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
                        Text(
                            "${index + 1}. ${track.title}",
                            modifier = Modifier.weight(1f)
                        )

                        if (
                            local != null &&
                            connectionManager.canControlPlayback()
                        ) {
                            Button(
                                enabled = !connectionManager.isBusy,
                                onClick = { onPlayRoomTrack(local) }
                            ) { Text("Играть") }
                        }
                    }
                }
            }

            Divider()
            Text("Добавить ещё песни в комнату")

            if (!connectionManager.canShareMusic()) {
                Text("Только админы могут добавлять треки.")
            } else {
                val alreadyInRoom = connectionManager.roomTracks
                    .map { it.id }
                    .toSet()
                val candidates = localSongs.filter { it.id !in alreadyInRoom }

                if (candidates.isEmpty()) {
                    Text("Все локальные песни уже в комнате.")
                } else {
                    candidates.forEach { song ->
                        val selected = song.id in selectedShareIds
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !connectionManager.isBusy,
                            onClick = {
                                selectedShareIds = if (selected) {
                                    selectedShareIds - song.id
                                } else {
                                    selectedShareIds + song.id
                                }
                            }
                        ) {
                            Text(
                                (if (selected) "✓ " else "○ ") + song.title
                            )
                        }
                    }

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !connectionManager.isBusy &&
                                selectedShareIds.isNotEmpty(),
                        onClick = {
                            val toSend = localSongs.filter {
                                it.id in selectedShareIds
                            }
                            connectionManager.shareSongs(toSend) { _, _ ->
                                selectedShareIds = emptySet()
                            }
                        }
                    ) {
                        Text("Отправить выбранные (${selectedShareIds.size})")
                    }
                }
            }
        }
    }
}

/**
 * После createRoom, когда появится currentRoomCode и isBusy=false,
 * один раз заливает выбранные seed-треки.
 */
@Composable
private fun PendingSeedUploader(
    connectionManager: RoomController,
    seedSongs: List<Song>
) {
    var uploadedForCode by remember { mutableStateOf<String?>(null) }
    val code = connectionManager.currentRoomCode
    val busy = connectionManager.isBusy

    if (
        code != null &&
        !busy &&
        seedSongs.isNotEmpty() &&
        uploadedForCode != code &&
        connectionManager.canShareMusic()
    ) {
        // Запускаем один раз на код комнаты
        androidx.compose.runtime.LaunchedEffect(code) {
            if (uploadedForCode == code) return@LaunchedEffect
            uploadedForCode = code
            // Небольшая пауза, чтобы Firebase listeners успели сесть
            kotlinx.coroutines.delay(400)
            connectionManager.shareSongs(seedSongs)
        }
    }
}

private fun roleLabel(role: RoomRole): String = when (role) {
    RoomRole.ADMIN -> "админ"
    RoomRole.MODERATOR -> "мл. админ"
    RoomRole.MEMBER -> "участник"
}
