package com.example.sharedmusic.logic.room.playlist

import com.example.sharedmusic.logic.room.RoomController

import android.os.Handler
import com.example.sharedmusic.logic.model.RoomTrack
import com.example.sharedmusic.logic.model.Song
import com.example.sharedmusic.logic.transfer.SupabaseStorage
import com.example.sharedmusic.logic.transfer.TransferApi
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService

// Загрузка и скачивание треков комнаты через Supabase + Firebase tracks
class PlaylistManager(
    private val room: RoomController
) {
    fun shareSong(song: Song) {
        val roomCode = room.currentRoomCode
        val uid = room.auth.currentUser?.uid

        if (roomCode == null || uid == null) {
            room.statusMessage = "Сначала войдите в комнату."
            return
        }

        if (!room.canShareMusic()) {
            room.statusMessage = "Нет прав на отправку музыки (только админы)."
            return
        }

        if (room.isBusy) return

        room.isBusy = true
        room.progress = 0f
        room.progressText = "Подготовка файла..."
        room.statusMessage = "Отправка песни..."

        // Список uid слушателей на момент отправки (без отправителя)
        val recipientUids = room.connectedDevices
            .map { it.id }
            .filter { it != uid }
            .distinct()

        room.worker.execute {
            var temporaryFile: File? = null

            try {
                val prepared = room.prepareSongFile(song)
                temporaryFile = prepared.first
                val fileName = prepared.second

                val uploadResult = room.storage.upload(
                    file = temporaryFile,
                    remoteFileName = fileName
                ) { uploaded, total ->
                    room.reportProgress("Загрузка", uploaded, total)
                }

                // Регистрируем передачу на сервере (для автоудаления)
                var transferId = ""
                try {
                    room.mainHandler.post {
                        room.progressText = "Регистрация передачи..."
                    }
                    val reg = room.transferApi.register(
                        roomCode = roomCode,
                        objectPath = uploadResult.objectPath,
                        downloadUrl = uploadResult.downloadUrl,
                        fileName = uploadResult.fileName,
                        songTitle = song.title,
                        senderUid = uid,
                        expectedRecipients = recipientUids,
                        ttlMinutes = 10
                    )
                    transferId = reg.transferId
                } catch (e: Exception) {
                    // Файл уже в Storage — всё равно публикуем в комнату,
                    // cleanup по таймауту подстрахует, если register упал.
                    room.mainHandler.post {
                        room.statusMessage =
                            "Файл загружен, но учёт передачи: " +
                                    (e.message ?: "ошибка")
                    }
                }

                val data = mapOf(
                    "songId" to song.id,
                    "title" to song.title,
                    "downloadUrl" to uploadResult.downloadUrl,
                    "addedBy" to uid,
                    "fileName" to uploadResult.fileName,
                    "addedAt" to System.currentTimeMillis(),
                    "transferId" to transferId,
                    "objectPath" to uploadResult.objectPath,
                    "order" to room.roomTracks.size
                )

                room.database.child("rooms")
                    .child(roomCode)
                    .child("tracks")
                    .child(room.firebaseKey(song.id))
                    .setValue(data)
                    .addOnSuccessListener {
                        room.isBusy = false
                        room.progress = 1f
                        room.progressText = ""
                        val extra = if (recipientUids.isEmpty()) {
                            " (получателей пока нет — файл удалится через 10 мин)"
                        } else {
                            " → ${recipientUids.size} слушател(ей)"
                        }
                        room.statusMessage = "Песня отправлена$extra."
                    }
                    .addOnFailureListener { error ->
                        room.isBusy = false
                        room.progressText = ""
                        room.statusMessage =
                            "Не удалось сохранить песню: ${error.message}"
                    }
            } catch (e: Exception) {
                room.mainHandler.post {
                    room.isBusy = false
                    room.progressText = ""
                    room.statusMessage =
                        "Ошибка отправки: " +
                                (e.message ?: "неизвестная ошибка")
                }
            } finally {
                temporaryFile?.delete()
            }
        }
    }


    fun shareSongs(
        songs: List<Song>,
        onFinished: ((Boolean, String) -> Unit)? = null
    ) {
        if (songs.isEmpty()) {
            onFinished?.invoke(true, "Нечего отправлять.")
            return
        }
        if (!room.canShareMusic()) {
            val msg = "Нет прав на отправку музыки (только админы)."
            room.statusMessage = msg
            onFinished?.invoke(false, msg)
            return
        }
        if (room.isBusy) {
            onFinished?.invoke(false, "Идёт другая операция.")
            return
        }

        val roomCode = room.currentRoomCode
        val uid = room.auth.currentUser?.uid
        if (roomCode == null || uid == null) {
            val msg = "Сначала войдите в комнату."
            room.statusMessage = msg
            onFinished?.invoke(false, msg)
            return
        }

        room.isBusy = true
        room.statusMessage = "Отправка ${songs.size} песен..."

        room.worker.execute {
            var successCount = 0
            var lastError: String? = null
            val baseOrder = room.roomTracks.size

            songs.forEachIndexed { index, song ->
                var temporaryFile: File? = null
                try {
                    room.mainHandler.post {
                        room.progress = 0f
                        room.progressText = "Песня ${index + 1}/${songs.size}: подготовка"
                        room.statusMessage = "Отправка ${index + 1}/${songs.size}..."
                    }

                    val prepared = room.prepareSongFile(song)
                    temporaryFile = prepared.first
                    val fileName = prepared.second

                    val uploadResult = room.storage.upload(
                        file = temporaryFile,
                        remoteFileName = fileName
                    ) { uploaded, total ->
                        room.reportProgress(
                            "Загрузка ${index + 1}/${songs.size}",
                            uploaded,
                            total
                        )
                    }

                    val recipientUids = room.connectedDevices
                        .map { it.id }
                        .filter { it != uid }
                        .distinct()

                    var transferId = ""
                    try {
                        val reg = room.transferApi.register(
                            roomCode = roomCode,
                            objectPath = uploadResult.objectPath,
                            downloadUrl = uploadResult.downloadUrl,
                            fileName = uploadResult.fileName,
                            songTitle = song.title,
                            senderUid = uid,
                            expectedRecipients = recipientUids,
                            ttlMinutes = 10
                        )
                        transferId = reg.transferId
                    } catch (_: Exception) {
                    }

                    val data = mapOf(
                        "songId" to song.id,
                        "title" to song.title,
                        "downloadUrl" to uploadResult.downloadUrl,
                        "addedBy" to uid,
                        "fileName" to uploadResult.fileName,
                        "addedAt" to System.currentTimeMillis(),
                        "transferId" to transferId,
                        "objectPath" to uploadResult.objectPath,
                        "order" to (baseOrder + index)
                    )

                    val latch = java.util.concurrent.CountDownLatch(1)
                    var writeOk = false
                    var writeError: String? = null

                    room.database.child("rooms")
                        .child(roomCode)
                        .child("tracks")
                        .child(room.firebaseKey(song.id))
                        .setValue(data)
                        .addOnSuccessListener {
                            writeOk = true
                            latch.countDown()
                        }
                        .addOnFailureListener { error ->
                            writeError = error.message
                            latch.countDown()
                        }

                    latch.await()
                    if (writeOk) {
                        successCount++
                    } else {
                        lastError = writeError ?: "ошибка записи"
                    }
                } catch (e: Exception) {
                    lastError = e.message ?: "ошибка"
                } finally {
                    temporaryFile?.delete()
                }
            }

            room.mainHandler.post {
                room.isBusy = false
                room.progress = 1f
                room.progressText = ""
                val msg = if (successCount == songs.size) {
                    "Отправлено песен: $successCount."
                } else {
                    "Отправлено $successCount из ${songs.size}." +
                            (lastError?.let { " Ошибка: $it" } ?: "")
                }
                room.statusMessage = msg
                onFinished?.invoke(successCount > 0, msg)
            }
        }
    }

    fun missingRoomTracks(localSongs: List<Song>): List<RoomTrack> {
        return room.roomTracks.filter { track ->
            localSongs.none { local ->
                local.id == track.id ||
                        local.title.trim().equals(
                            track.title.trim(),
                            ignoreCase = true
                        )
            }
        }
    }

    fun hasAllRoomTracks(localSongs: List<Song>): Boolean {
        if (room.roomTracks.isEmpty()) return true
        return missingRoomTracks(localSongs).isEmpty()
    }

    fun downloadMissingTracks(
        localSongs: List<Song>,
        onEachReady: (File, RoomTrack) -> Unit,
        onFinished: (Int, Int) -> Unit
    ) {
        val missing = missingRoomTracks(localSongs)
        if (missing.isEmpty()) {
            onFinished(0, 0)
            return
        }
        if (room.isBusy) return

        room.isBusy = true
        room.statusMessage = "Скачивание библиотеки комнаты..."

        room.worker.execute {
            var ok = 0
            for ((index, track) in missing.withIndex()) {
                try {
                    room.mainHandler.post {
                        room.progress = 0f
                        room.progressText =
                            "Скачивание ${index + 1}/${missing.size}: ${track.title}"
                        room.statusMessage =
                            "Скачивание ${index + 1}/${missing.size}..."
                    }

                    val directory = File(room.appContext.filesDir, "room_tracks")
                    if (!directory.exists()) directory.mkdirs()

                    val rawTitle = room.safeFilePart(track.title)
                    val baseTitle = rawTitle
                        .substringBeforeLast('.', rawTitle)
                        .ifBlank { "track" }
                    val extension = room.getExtension(track.fileName)
                    var destination = File(directory, "$baseTitle$extension")
                    var n = 2
                    while (destination.exists()) {
                        destination = File(
                            directory,
                            "$baseTitle ($n)$extension"
                        )
                        n++
                    }

                    val downloaded = room.storage.download(
                        downloadUrl = track.downloadUrl,
                        destinationFile = destination
                    ) { d, t ->
                        room.reportProgress(
                            "Скачивание ${index + 1}/${missing.size}",
                            d,
                            t
                        )
                    }

                    // confirm transfer if possible
                    val transferId = track.transferId
                    val objectPath = track.objectPath
                    val myUid = room.auth.currentUser?.uid
                    if (myUid != null) {
                        try {
                            if (transferId.isNotBlank()) {
                                room.transferApi.confirm(transferId, myUid)
                            } else if (objectPath.isNotBlank()) {
                                room.transferApi.deleteByPath(objectPath)
                            }
                        } catch (_: Exception) {
                        }
                    }

                    ok++
                    room.mainHandler.post {
                        onEachReady(downloaded, track)
                    }
                } catch (e: Exception) {
                    room.mainHandler.post {
                        room.statusMessage =
                            "Ошибка «${track.title}»: ${e.message}"
                    }
                }
            }

            room.mainHandler.post {
                room.isBusy = false
                room.progressText = ""
                room.progress = 1f
                room.statusMessage =
                    "Скачано $ok из ${missing.size} треков комнаты."
                onFinished(ok, missing.size)
            }
        }
    }

    fun downloadTrack(
        track: RoomTrack,
        onSongReady: (File) -> Unit
    ) {
        if (room.currentRoomCode == null) {
            room.statusMessage = "Сначала войдите в комнату."
            return
        }

        if (room.isBusy) return

        room.isBusy = true
        room.progress = 0f
        room.progressText = "Подготовка скачивания..."
        room.statusMessage = "Скачивание песни..."

        room.worker.execute {
            try {
                val directory = File(
                    room.appContext.filesDir,
                    "room_tracks"
                )

                if (!directory.exists() && !directory.mkdirs()) {
                    throw IOException(
                        "Не удалось создать папку для музыки"
                    )
                }

                val rawTitle = room.safeFilePart(track.title)
                val baseTitle = rawTitle
                    .substringBeforeLast('.', rawTitle)
                    .ifBlank { "track" }

                val extension = room.getExtension(track.fileName)

                var destination = File(
                    directory,
                    "$baseTitle$extension"
                )

                var index = 2

                while (destination.exists()) {
                    destination = File(
                        directory,
                        "$baseTitle ($index)$extension"
                    )
                    index++
                }

                val downloadedFile = room.storage.download(
                    downloadUrl = track.downloadUrl,
                    destinationFile = destination
                ) { downloaded, total ->
                    room.reportProgress("Скачивание", downloaded, total)
                }

                // Подтверждаем скачивание / удаляем файл с сервера
                val transferId = track.transferId
                val objectPath = track.objectPath
                val myUid = room.auth.currentUser?.uid
                var serverNote = ""

                if (myUid != null) {
                    try {
                        if (transferId.isNotBlank()) {
                            val confirm = room.transferApi.confirm(
                                transferId = transferId,
                                recipientUid = myUid
                            )
                            serverNote = if (confirm.deleted) {
                                " Файл удалён с сервера."
                            } else {
                                " Подтверждено ${confirm.confirmedCount}/" +
                                        "${confirm.expectedCount}."
                            }
                        } else if (objectPath.isNotBlank()) {
                            // register не сработал — удаляем по пути
                            room.transferApi.deleteByPath(objectPath)
                            serverNote = " Файл удалён с сервера (по пути)."
                        } else {
                            serverNote =
                                " (нет transferId — файл удалится по таймеру ~10 мин)"
                        }
                    } catch (e: Exception) {
                        // Пробуем запасной путь
                        try {
                            if (objectPath.isNotBlank()) {
                                room.transferApi.deleteByPath(
                                    objectPath = objectPath,
                                    transferId = transferId.ifBlank { null }
                                )
                                serverNote =
                                    " Файл удалён с сервера (запасной путь)."
                            } else {
                                serverNote =
                                    " Confirm ошибка: ${e.message}"
                            }
                        } catch (e2: Exception) {
                            serverNote =
                                " Не удалось удалить на сервере: ${e2.message}"
                        }
                    }
                }

                room.mainHandler.post {
                    try {
                        onSongReady(downloadedFile)
                        room.progress = 1f
                        room.progressText = ""
                        room.statusMessage = "Песня скачана.$serverNote"
                    } catch (e: Exception) {
                        room.statusMessage =
                            "Файл скачан, но не добавлен: ${e.message}"
                    } finally {
                        room.isBusy = false
                    }
                }
            } catch (e: Exception) {
                room.mainHandler.post {
                    room.isBusy = false
                    room.progressText = ""
                    room.statusMessage =
                        "Ошибка скачивания: " +
                                (e.message ?: "неизвестная ошибка")
                }
            }
        }
    }


}
