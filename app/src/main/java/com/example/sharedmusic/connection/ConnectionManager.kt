// Механика партнёра SharedMusic: передача, синхронизация и воспроизведение.
package com.example.sharedmusic.connection

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.sharedmusic.model.Song
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

data class RoomDevice(
    val id: String,
    val name: String
)

data class RoomTrack(
    val id: String,
    val title: String,
    val downloadUrl: String,
    val addedBy: String,
    val fileName: String = ""
)

data class RemotePlaybackCommand(
    val trackId: String,
    val title: String,
    val positionMs: Long,
    val isPlaying: Boolean,
    val updatedAt: Long,
    val updatedBy: String,
    val revision: String,
    val receivedAtMs: Long = System.currentTimeMillis()
)

class ConnectionManager(context: Context) {

    companion object {
        private const val DATABASE_URL =
            "https://shary-f5c42-default-rtdb.europe-west1.firebasedatabase.app/"

        private const val ROOM_CODE_LENGTH = 4

        private const val ROOM_CHARACTERS =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }

    private val appContext = context.applicationContext
    private val auth = FirebaseAuth.getInstance()

    private val database = FirebaseDatabase
        .getInstance(DATABASE_URL)
        .reference

    private val uploader = FileUploader()
    private val worker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val random = SecureRandom()

    var currentRoomCode by mutableStateOf<String?>(null)
        private set

    var statusMessage by mutableStateOf(
        "Создайте комнату или войдите по коду."
    )
        private set

    var isBusy by mutableStateOf(false)
        private set

    var progress by mutableStateOf(0f)
        private set

    var progressText by mutableStateOf("")
        private set

    val connectedDevices = mutableStateListOf<RoomDevice>()
    val roomTracks = mutableStateListOf<RoomTrack>()

    private var devicesReference: DatabaseReference? = null
    private var tracksReference: DatabaseReference? = null
    private var playbackStateReference: DatabaseReference? = null

    private var devicesListener: ValueEventListener? = null
    private var tracksListener: ValueEventListener? = null
    private var playbackStateListener: ValueEventListener? = null

    private var playbackCallback:
            ((RemotePlaybackCommand) -> Unit)? = null

    private var latestPlaybackAuthor: String? = null
    private var latestPlaybackRevision: String? = null
    private var serverTimeOffsetMs = 0L

    private val serverTimeOffsetReference = database
        .child(".info")
        .child("serverTimeOffset")

    private val serverTimeOffsetListener =
        object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {
                serverTimeOffsetMs =
                    (snapshot.value as? Number)?.toLong() ?: 0L
            }

            override fun onCancelled(error: DatabaseError) {
                // При недоступности offset используется локальное время.
            }
        }

    init {
        serverTimeOffsetReference.addValueEventListener(
            serverTimeOffsetListener
        )
    }

    fun setPlaybackListener(
        listener: ((RemotePlaybackCommand) -> Unit)?
    ) {
        playbackCallback = listener
    }

    fun isLatestPlaybackCommand(
        command: RemotePlaybackCommand
    ): Boolean {
        return latestPlaybackRevision == command.revision
    }

    fun createRoom() {
        if (isBusy || currentRoomCode != null) return

        isBusy = true
        statusMessage = "Создание комнаты..."

        ensureSignedIn { uid ->
            findAvailableRoom(uid, 10)
        }
    }

    fun joinRoom(inputCode: String) {
        if (isBusy || currentRoomCode != null) return

        val code = inputCode.trim().uppercase(Locale.ROOT)

        if (
            code.length != ROOM_CODE_LENGTH ||
            code.any { it !in ROOM_CHARACTERS }
        ) {
            statusMessage = "Введите корректный код из 4 символов."
            return
        }

        isBusy = true
        statusMessage = "Подключение к комнате $code..."

        ensureSignedIn { uid ->
            database.child("rooms")
                .child(code)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (!snapshot.exists()) {
                        isBusy = false
                        statusMessage = "Комната $code не найдена."
                    } else {
                        registerDevice(code, uid)
                    }
                }
                .addOnFailureListener { error ->
                    isBusy = false
                    statusMessage =
                        "Ошибка входа: ${error.message}"
                }
        }
    }

    // Вызывается, когда пользователь локально запускает,
    // останавливает или перематывает песню.
    fun publishPlaybackState(
        song: Song,
        positionMs: Long,
        isPlaying: Boolean
    ) {
        val uid = auth.currentUser?.uid ?: return
        val roomCode = currentRoomCode ?: return

        writePlaybackState(
            roomCode = roomCode,
            uid = uid,
            song = song,
            positionMs = positionMs,
            isPlaying = isPlaying
        )
    }

    // Периодическая коррекция позиции отправляется только участником,
    // который последним управлял воспроизведением.
    fun publishHeartbeat(
        song: Song,
        positionMs: Long
    ) {
        val uid = auth.currentUser?.uid ?: return
        val roomCode = currentRoomCode ?: return

        if (latestPlaybackAuthor != uid) return

        writePlaybackState(
            roomCode = roomCode,
            uid = uid,
            song = song,
            positionMs = positionMs,
            isPlaying = true
        )
    }

    private fun writePlaybackState(
        roomCode: String,
        uid: String,
        song: Song,
        positionMs: Long,
        isPlaying: Boolean
    ) {
        val revision = UUID.randomUUID().toString()

        latestPlaybackAuthor = uid
        latestPlaybackRevision = revision

        val data = mapOf(
            "trackId" to song.id,
            "title" to song.title,
            "positionMs" to positionMs.coerceAtLeast(0L),
            "isPlaying" to isPlaying,
            "updatedAt" to ServerValue.TIMESTAMP,
            "updatedBy" to uid,
            "revision" to revision
        )

        database.child("rooms")
            .child(roomCode)
            .child("playbackState")
            .setValue(data)
            .addOnFailureListener { error ->
                mainHandler.post {
                    statusMessage =
                        "Не удалось синхронизировать плеер: " +
                                error.message
                }
            }
    }

    fun shareSong(song: Song) {
        val roomCode = currentRoomCode
        val uid = auth.currentUser?.uid

        if (roomCode == null || uid == null) {
            statusMessage = "Сначала войдите в комнату."
            return
        }

        if (isBusy) return

        isBusy = true
        progress = 0f
        progressText = "Подготовка файла..."
        statusMessage = "Отправка песни..."

        worker.execute {
            var temporaryFile: File? = null

            try {
                val prepared = prepareSongFile(song)
                temporaryFile = prepared.first
                val fileName = prepared.second

                val downloadUrl = uploader.upload(
                    file = temporaryFile,
                    remoteFileName = fileName
                ) { uploaded, total ->
                    reportProgress("Загрузка", uploaded, total)
                }

                val data = mapOf(
                    "songId" to song.id,
                    "title" to song.title,
                    "downloadUrl" to downloadUrl,
                    "addedBy" to uid,
                    "fileName" to fileName,
                    "addedAt" to System.currentTimeMillis()
                )

                database.child("rooms")
                    .child(roomCode)
                    .child("tracks")
                    .child(firebaseKey(song.id))
                    .setValue(data)
                    .addOnSuccessListener {
                        isBusy = false
                        progress = 1f
                        progressText = ""
                        statusMessage = "Песня отправлена в комнату."
                    }
                    .addOnFailureListener { error ->
                        isBusy = false
                        progressText = ""
                        statusMessage =
                            "Не удалось сохранить песню: ${error.message}"
                    }
            } catch (e: Exception) {
                mainHandler.post {
                    isBusy = false
                    progressText = ""
                    statusMessage =
                        "Ошибка отправки: " +
                                (e.message ?: "неизвестная ошибка")
                }
            } finally {
                temporaryFile?.delete()
            }
        }
    }

    fun downloadTrack(
        track: RoomTrack,
        onSongReady: (File) -> Unit
    ) {
        if (currentRoomCode == null) {
            statusMessage = "Сначала войдите в комнату."
            return
        }

        if (isBusy) return

        isBusy = true
        progress = 0f
        progressText = "Подготовка скачивания..."
        statusMessage = "Скачивание песни..."

        worker.execute {
            try {
                val directory = File(
                    appContext.filesDir,
                    "room_tracks"
                )

                if (!directory.exists() && !directory.mkdirs()) {
                    throw IOException(
                        "Не удалось создать папку для музыки"
                    )
                }

                val rawTitle = safeFilePart(track.title)
                val baseTitle = rawTitle
                    .substringBeforeLast('.', rawTitle)
                    .ifBlank { "track" }

                val extension = getExtension(track.fileName)

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

                val downloadedFile = uploader.download(
                    downloadUrl = track.downloadUrl,
                    destinationFile = destination
                ) { downloaded, total ->
                    reportProgress("Скачивание", downloaded, total)
                }

                mainHandler.post {
                    try {
                        onSongReady(downloadedFile)
                        progress = 1f
                        progressText = ""
                        statusMessage = "Песня скачана."
                    } catch (e: Exception) {
                        statusMessage =
                            "Файл скачан, но не добавлен: ${e.message}"
                    } finally {
                        isBusy = false
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    isBusy = false
                    progressText = ""
                    statusMessage =
                        "Ошибка скачивания: " +
                                (e.message ?: "неизвестная ошибка")
                }
            }
        }
    }

    fun leaveRoom() {
        val code = currentRoomCode
        val uid = auth.currentUser?.uid

        removeRoomListeners()

        currentRoomCode = null
        connectedDevices.clear()
        roomTracks.clear()
        progress = 0f
        progressText = ""

        latestPlaybackAuthor = null
        latestPlaybackRevision = null

        if (code != null && uid != null) {
            val deviceReference = database
                .child("rooms")
                .child(code)
                .child("devices")
                .child(uid)

            deviceReference.onDisconnect().cancel()
            deviceReference.removeValue()
        }

        statusMessage = "Вы вышли из комнаты."
    }

    fun dispose() {
        leaveRoom()
        playbackCallback = null

        serverTimeOffsetReference.removeEventListener(
            serverTimeOffsetListener
        )

        worker.shutdownNow()
    }

    private fun ensureSignedIn(onReady: (String) -> Unit) {
        val user = auth.currentUser

        if (user != null) {
            onReady(user.uid)
            return
        }

        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                val uid = result.user?.uid

                if (uid == null) {
                    isBusy = false
                    statusMessage = "Firebase не вернул ID пользователя."
                } else {
                    onReady(uid)
                }
            }
            .addOnFailureListener { error ->
                isBusy = false
                statusMessage =
                    "Ошибка авторизации: ${error.message}"
            }
    }

    private fun findAvailableRoom(
        uid: String,
        attemptsLeft: Int
    ) {
        if (attemptsLeft <= 0) {
            isBusy = false
            statusMessage = "Не удалось создать свободную комнату."
            return
        }

        val code = generateRoomCode()
        val roomReference = database.child("rooms").child(code)

        roomReference.get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    findAvailableRoom(uid, attemptsLeft - 1)
                    return@addOnSuccessListener
                }

                val info = mapOf(
                    "createdBy" to uid,
                    "createdAt" to System.currentTimeMillis()
                )

                roomReference.child("info")
                    .setValue(info)
                    .addOnSuccessListener {
                        registerDevice(code, uid)
                    }
                    .addOnFailureListener { error ->
                        isBusy = false
                        statusMessage =
                            "Ошибка создания комнаты: ${error.message}"
                    }
            }
            .addOnFailureListener { error ->
                isBusy = false
                statusMessage =
                    "Ошибка Firebase: ${error.message}"
            }
    }

    private fun registerDevice(
        code: String,
        uid: String
    ) {
        val deviceReference = database
            .child("rooms")
            .child(code)
            .child("devices")
            .child(uid)

        val deviceData = mapOf(
            "name" to getDeviceName(),
            "joinedAt" to System.currentTimeMillis()
        )

        deviceReference.onDisconnect()
            .removeValue()
            .addOnSuccessListener {
                deviceReference.setValue(deviceData)
                    .addOnSuccessListener {
                        currentRoomCode = code
                        isBusy = false
                        statusMessage = "Вы подключены к комнате $code."
                        observeRoom(code)
                    }
                    .addOnFailureListener { error ->
                        isBusy = false
                        statusMessage =
                            "Не удалось подключить устройство: " +
                                    error.message
                    }
            }
            .addOnFailureListener { error ->
                isBusy = false
                statusMessage =
                    "Ошибка настройки соединения: ${error.message}"
            }
    }

    private fun observeRoom(code: String) {
        removeRoomListeners()

        val roomReference = database.child("rooms").child(code)

        devicesReference = roomReference.child("devices")
        tracksReference = roomReference.child("tracks")
        playbackStateReference = roomReference.child("playbackState")

        devicesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                connectedDevices.clear()

                for (child in snapshot.children) {
                    val id = child.key ?: continue

                    val name = child.child("name")
                        .getValue(String::class.java)
                        ?: "Устройство"

                    connectedDevices.add(
                        RoomDevice(id, name)
                    )
                }
            }

            override fun onCancelled(error: DatabaseError) {
                statusMessage =
                    "Ошибка получения устройств: ${error.message}"
            }
        }

        tracksListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                roomTracks.clear()

                for (child in snapshot.children) {
                    val key = child.key ?: continue

                    val title = child.child("title")
                        .getValue(String::class.java)
                        ?: continue

                    val url = child.child("downloadUrl")
                        .getValue(String::class.java)
                        ?: continue

                    val addedBy = child.child("addedBy")
                        .getValue(String::class.java)
                        ?: ""

                    val songId = child.child("songId")
                        .getValue(String::class.java)
                        ?: key

                    val fileName = child.child("fileName")
                        .getValue(String::class.java)
                        ?: ""

                    roomTracks.add(
                        RoomTrack(
                            id = songId,
                            title = title,
                            downloadUrl = url,
                            addedBy = addedBy,
                            fileName = fileName
                        )
                    )
                }
            }

            override fun onCancelled(error: DatabaseError) {
                statusMessage =
                    "Ошибка получения песен: ${error.message}"
            }
        }

        playbackStateListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return

                val trackId = snapshot.child("trackId")
                    .getValue(String::class.java)
                    ?: return

                val title = snapshot.child("title")
                    .getValue(String::class.java)
                    ?: return

                val updatedBy = snapshot.child("updatedBy")
                    .getValue(String::class.java)
                    ?: return

                val revision = snapshot.child("revision")
                    .getValue(String::class.java)
                    ?: return

                val positionMs = (
                        snapshot.child("positionMs").value as? Number
                        )?.toLong() ?: 0L

                val updatedAt = (
                        snapshot.child("updatedAt").value as? Number
                        )?.toLong() ?: 0L

                val isPlaying = snapshot.child("isPlaying")
                    .getValue(Boolean::class.java)
                    ?: false

                latestPlaybackAuthor = updatedBy
                latestPlaybackRevision = revision

                // Не обрабатываем собственные записи.
                if (updatedBy == auth.currentUser?.uid) return

                val currentServerTime =
                    System.currentTimeMillis() + serverTimeOffsetMs

                val elapsedMs = if (isPlaying && updatedAt > 0L) {
                    (currentServerTime - updatedAt)
                        .coerceIn(0L, 30_000L)
                } else {
                    0L
                }

                val command = RemotePlaybackCommand(
                    trackId = trackId,
                    title = title,
                    positionMs = positionMs + elapsedMs,
                    isPlaying = isPlaying,
                    updatedAt = updatedAt,
                    updatedBy = updatedBy,
                    revision = revision
                )

                playbackCallback?.invoke(command)
            }

            override fun onCancelled(error: DatabaseError) {
                statusMessage =
                    "Ошибка синхронизации плеера: ${error.message}"
            }
        }

        devicesReference?.addValueEventListener(devicesListener!!)
        tracksReference?.addValueEventListener(tracksListener!!)
        playbackStateReference?.addValueEventListener(
            playbackStateListener!!
        )
    }

    private fun removeRoomListeners() {
        devicesListener?.let {
            devicesReference?.removeEventListener(it)
        }

        tracksListener?.let {
            tracksReference?.removeEventListener(it)
        }

        playbackStateListener?.let {
            playbackStateReference?.removeEventListener(it)
        }

        devicesListener = null
        tracksListener = null
        playbackStateListener = null

        devicesReference = null
        tracksReference = null
        playbackStateReference = null
    }

    private fun prepareSongFile(song: Song): Pair<File, String> {
        val fileName = safeFilePart(
            getDisplayName(song.uri, song.title)
        )

        val safeName = if (fileName.contains('.')) {
            fileName
        } else {
            "$fileName.audio"
        }

        val temporaryFile = File(
            appContext.cacheDir,
            "sharedmusic_${UUID.randomUUID()}_$safeName"
        )

        try {
            val input = appContext.contentResolver
                .openInputStream(song.uri)
                ?: throw IOException("Не удалось открыть музыкальный файл")

            input.use { source ->
                temporaryFile.outputStream().use { output ->
                    source.copyTo(output)
                }
            }

            if (temporaryFile.length() == 0L) {
                throw IOException("Исходный музыкальный файл пуст")
            }

            return Pair(temporaryFile, safeName)
        } catch (e: Exception) {
            temporaryFile.delete()
            throw e
        }
    }

    private fun getDisplayName(
        uri: Uri,
        fallbackTitle: String
    ): String {
        var cursor: Cursor? = null

        return try {
            cursor = appContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )

            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(
                    OpenableColumns.DISPLAY_NAME
                )

                if (index >= 0) {
                    cursor.getString(index) ?: "$fallbackTitle.audio"
                } else {
                    "$fallbackTitle.audio"
                }
            } else {
                uri.lastPathSegment
                    ?.substringAfterLast('/')
                    ?: "$fallbackTitle.audio"
            }
        } finally {
            cursor?.close()
        }
    }

    private fun generateRoomCode(): String {
        return buildString {
            repeat(ROOM_CODE_LENGTH) {
                append(
                    ROOM_CHARACTERS[
                        random.nextInt(ROOM_CHARACTERS.length)
                    ]
                )
            }
        }
    }

    private fun firebaseKey(value: String): String {
        val result = value.map { char ->
            when (char) {
                '.', '#', '$', '[', ']', '/', '\\' -> '_'
                else -> char
            }
        }.joinToString("")

        return result.ifBlank {
            UUID.randomUUID().toString()
        }
    }

    private fun safeFilePart(value: String): String {
        val result = value.map { char ->
            when (char) {
                '/', '\\', ':', '*', '?', '"', '<', '>', '|' -> '_'
                else -> char
            }
        }.joinToString("").trim()

        return result.ifBlank { "track" }
    }

    private fun getExtension(fileName: String): String {
        val extension = fileName
            .substringAfterLast('.', "")
            .takeIf { it.isNotBlank() && it.length <= 10 }

        return if (extension == null) {
            ".audio"
        } else {
            ".$extension"
        }
    }

    private fun getDeviceName(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}"
            .trim()
            .ifBlank { "Android device" }
    }

    private fun reportProgress(
        label: String,
        currentBytes: Long,
        totalBytes: Long
    ) {
        mainHandler.post {
            progress = if (totalBytes > 0L) {
                (currentBytes.toFloat() / totalBytes.toFloat())
                    .coerceIn(0f, 1f)
            } else {
                0f
            }

            progressText = if (totalBytes > 0L) {
                "$label: ${formatBytes(currentBytes)} / " +
                        formatBytes(totalBytes)
            } else {
                "$label: ${formatBytes(currentBytes)}"
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"

        val kb = bytes / 1024.0

        if (kb < 1024.0) {
            return String.format(
                Locale.getDefault(),
                "%.1f KB",
                kb
            )
        }

        return String.format(
            Locale.getDefault(),
            "%.1f MB",
            kb / 1024.0
        )
    }
}