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
import com.example.sharedmusic.transfer.TransferClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

data class RoomTrack(
    val id: String,
    val title: String,
    val downloadUrl: String,
    val addedBy: String,
    val fileName: String = "",
    /** ID передачи в Supabase transfer_jobs (для confirm после скачивания) */
    val transferId: String = "",
    /** Путь объекта в Storage — запасной вариант удаления */
    val objectPath: String = ""
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
            "https://sharedmusic-38102-default-rtdb.europe-west1.firebasedatabase.app"

        private const val ROOM_CODE_LENGTH = 4

        private const val ROOM_CHARACTERS =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        const val MIN_CAPACITY = 2
        const val MAX_CAPACITY = 20
        const val DEFAULT_CAPACITY = 5
    }

    private val appContext = context.applicationContext
    private val auth = FirebaseAuth.getInstance()

    private val database = FirebaseDatabase
        .getInstance(DATABASE_URL)
        .reference

    private val uploader = FileUploader()
    private val transferClient = TransferClient()
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

    /** Вместимость текущей комнаты (включая владельца). 0 = ещё не в комнате. */
    var roomCapacity by mutableStateOf(0)
        private set

    /** Режим прав: OPEN или ADMIN_ONLY */
    var permissionMode by mutableStateOf(RoomPermissionMode.OPEN)
        private set

    /** Роль текущего пользователя в комнате */
    var myRole by mutableStateOf(RoomRole.MEMBER)
        private set

    /** uid текущего админа комнаты */
    var adminUid by mutableStateOf<String?>(null)
        private set

    /** Есть ли пароль у комнаты (не сам пароль) */
    var roomHasPassword by mutableStateOf(false)
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

    fun createRoom(
        maxCapacity: Int = DEFAULT_CAPACITY,
        password: String = "",
        permissionMode: RoomPermissionMode = RoomPermissionMode.OPEN
    ) {
        if (isBusy || currentRoomCode != null) return

        val capacity = maxCapacity.coerceIn(MIN_CAPACITY, MAX_CAPACITY)

        isBusy = true
        statusMessage = "Создание комнаты (до $capacity чел.)..."

        ensureSignedIn { uid ->
            findAvailableRoom(
                uid = uid,
                maxCapacity = capacity,
                password = password,
                permissionMode = permissionMode,
                attemptsLeft = 10
            )
        }
    }

    fun joinRoom(inputCode: String, password: String = "") {
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
                        return@addOnSuccessListener
                    }

                    val info = snapshot.child("info")
                    val capacity = (
                            info.child("maxCapacity").value as? Number
                            )?.toInt() ?: DEFAULT_CAPACITY

                    val storedHash = info.child("passwordHash")
                        .getValue(String::class.java)
                        .orEmpty()

                    if (storedHash.isNotBlank()) {
                        val given = hashPassword(password)
                        if (given != storedHash) {
                            isBusy = false
                            statusMessage = "Неверный пароль комнаты."
                            return@addOnSuccessListener
                        }
                    }

                    val deviceCount = snapshot.child("devices")
                        .childrenCount.toInt()

                    if (
                        !snapshot.child("devices").child(uid).exists() &&
                        deviceCount >= capacity
                    ) {
                        isBusy = false
                        statusMessage =
                            "Комната $code заполнена ($deviceCount/$capacity)."
                        return@addOnSuccessListener
                    }

                    val mode = RoomPermissionMode.fromString(
                        info.child("permissionMode").getValue(String::class.java)
                    )
                    val admin = info.child("adminUid")
                        .getValue(String::class.java)

                    registerDevice(
                        code = code,
                        uid = uid,
                        maxCapacity = capacity,
                        role = RoomRole.MEMBER,
                        permissionMode = mode,
                        adminUidValue = admin,
                        hasPassword = storedHash.isNotBlank()
                    )
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
    /** Можно ли управлять плеером (play/pause/seek) */
    fun canControlPlayback(): Boolean {
        if (currentRoomCode == null) return false
        return when (permissionMode) {
            RoomPermissionMode.OPEN -> true
            RoomPermissionMode.ADMIN_ONLY -> myRole.canModerate()
        }
    }

    /** Можно ли отправлять музыку в комнату */
    fun canShareMusic(): Boolean {
        if (currentRoomCode == null) return false
        return when (permissionMode) {
            RoomPermissionMode.OPEN -> true
            RoomPermissionMode.ADMIN_ONLY -> myRole.canModerate()
        }
    }

    /** Только полный админ может назначать младших */
    fun canManageRoles(): Boolean {
        return currentRoomCode != null && myRole == RoomRole.ADMIN
    }

    fun publishPlaybackState(
        song: Song,
        positionMs: Long,
        isPlaying: Boolean
    ) {
        val uid = auth.currentUser?.uid ?: return
        val roomCode = currentRoomCode ?: return

        if (!canControlPlayback()) {
            statusMessage = "Нет прав на управление музыкой (только админы)."
            return
        }

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

        if (!canControlPlayback()) return
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

        if (!canShareMusic()) {
            statusMessage = "Нет прав на отправку музыки (только админы)."
            return
        }

        if (isBusy) return

        isBusy = true
        progress = 0f
        progressText = "Подготовка файла..."
        statusMessage = "Отправка песни..."

        // Список uid слушателей на момент отправки (без отправителя)
        val recipientUids = connectedDevices
            .map { it.id }
            .filter { it != uid }
            .distinct()

        worker.execute {
            var temporaryFile: File? = null

            try {
                val prepared = prepareSongFile(song)
                temporaryFile = prepared.first
                val fileName = prepared.second

                val uploadResult = uploader.upload(
                    file = temporaryFile,
                    remoteFileName = fileName
                ) { uploaded, total ->
                    reportProgress("Загрузка", uploaded, total)
                }

                // Регистрируем передачу на сервере (для автоудаления)
                var transferId = ""
                try {
                    mainHandler.post {
                        progressText = "Регистрация передачи..."
                    }
                    val reg = transferClient.register(
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
                    mainHandler.post {
                        statusMessage =
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
                    "order" to roomTracks.size
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
                        val extra = if (recipientUids.isEmpty()) {
                            " (получателей пока нет — файл удалится через 10 мин)"
                        } else {
                            " → ${recipientUids.size} слушател(ей)"
                        }
                        statusMessage = "Песня отправлена$extra."
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


    /**
     * Отправляет несколько песен в комнату по очереди.
     * @param onFinished вызывается на main thread когда всё готово или при ошибке.
     */
    fun shareSongs(
        songs: List<Song>,
        onFinished: ((Boolean, String) -> Unit)? = null
    ) {
        if (songs.isEmpty()) {
            onFinished?.invoke(true, "Нечего отправлять.")
            return
        }
        if (!canShareMusic()) {
            val msg = "Нет прав на отправку музыки (только админы)."
            statusMessage = msg
            onFinished?.invoke(false, msg)
            return
        }
        if (isBusy) {
            onFinished?.invoke(false, "Идёт другая операция.")
            return
        }

        val roomCode = currentRoomCode
        val uid = auth.currentUser?.uid
        if (roomCode == null || uid == null) {
            val msg = "Сначала войдите в комнату."
            statusMessage = msg
            onFinished?.invoke(false, msg)
            return
        }

        isBusy = true
        statusMessage = "Отправка ${songs.size} песен..."

        worker.execute {
            var successCount = 0
            var lastError: String? = null
            val baseOrder = roomTracks.size

            songs.forEachIndexed { index, song ->
                var temporaryFile: File? = null
                try {
                    mainHandler.post {
                        progress = 0f
                        progressText = "Песня ${index + 1}/${songs.size}: подготовка"
                        statusMessage = "Отправка ${index + 1}/${songs.size}..."
                    }

                    val prepared = prepareSongFile(song)
                    temporaryFile = prepared.first
                    val fileName = prepared.second

                    val uploadResult = uploader.upload(
                        file = temporaryFile,
                        remoteFileName = fileName
                    ) { uploaded, total ->
                        reportProgress(
                            "Загрузка ${index + 1}/${songs.size}",
                            uploaded,
                            total
                        )
                    }

                    val recipientUids = connectedDevices
                        .map { it.id }
                        .filter { it != uid }
                        .distinct()

                    var transferId = ""
                    try {
                        val reg = transferClient.register(
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

                    database.child("rooms")
                        .child(roomCode)
                        .child("tracks")
                        .child(firebaseKey(song.id))
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

            mainHandler.post {
                isBusy = false
                progress = 1f
                progressText = ""
                val msg = if (successCount == songs.size) {
                    "Отправлено песен: $successCount."
                } else {
                    "Отправлено $successCount из ${songs.size}." +
                            (lastError?.let { " Ошибка: $it" } ?: "")
                }
                statusMessage = msg
                onFinished?.invoke(successCount > 0, msg)
            }
        }
    }

    /**
     * Треки комнаты, которых ещё нет в локальной библиотеке.
     */
    fun missingRoomTracks(localSongs: List<Song>): List<RoomTrack> {
        return roomTracks.filter { track ->
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
        if (roomTracks.isEmpty()) return true
        return missingRoomTracks(localSongs).isEmpty()
    }

    /**
     * Скачивает все недостающие треки комнаты по очереди.
     */
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
        if (isBusy) return

        isBusy = true
        statusMessage = "Скачивание библиотеки комнаты..."

        worker.execute {
            var ok = 0
            for ((index, track) in missing.withIndex()) {
                try {
                    mainHandler.post {
                        progress = 0f
                        progressText =
                            "Скачивание ${index + 1}/${missing.size}: ${track.title}"
                        statusMessage =
                            "Скачивание ${index + 1}/${missing.size}..."
                    }

                    val directory = File(appContext.filesDir, "room_tracks")
                    if (!directory.exists()) directory.mkdirs()

                    val rawTitle = safeFilePart(track.title)
                    val baseTitle = rawTitle
                        .substringBeforeLast('.', rawTitle)
                        .ifBlank { "track" }
                    val extension = getExtension(track.fileName)
                    var destination = File(directory, "$baseTitle$extension")
                    var n = 2
                    while (destination.exists()) {
                        destination = File(
                            directory,
                            "$baseTitle ($n)$extension"
                        )
                        n++
                    }

                    val downloaded = uploader.download(
                        downloadUrl = track.downloadUrl,
                        destinationFile = destination
                    ) { d, t ->
                        reportProgress(
                            "Скачивание ${index + 1}/${missing.size}",
                            d,
                            t
                        )
                    }

                    // confirm transfer if possible
                    val transferId = track.transferId
                    val objectPath = track.objectPath
                    val myUid = auth.currentUser?.uid
                    if (myUid != null) {
                        try {
                            if (transferId.isNotBlank()) {
                                transferClient.confirm(transferId, myUid)
                            } else if (objectPath.isNotBlank()) {
                                transferClient.deleteByPath(objectPath)
                            }
                        } catch (_: Exception) {
                        }
                    }

                    ok++
                    mainHandler.post {
                        onEachReady(downloaded, track)
                    }
                } catch (e: Exception) {
                    mainHandler.post {
                        statusMessage =
                            "Ошибка «${track.title}»: ${e.message}"
                    }
                }
            }

            mainHandler.post {
                isBusy = false
                progressText = ""
                progress = 1f
                statusMessage =
                    "Скачано $ok из ${missing.size} треков комнаты."
                onFinished(ok, missing.size)
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

                // Подтверждаем скачивание / удаляем файл с сервера
                val transferId = track.transferId
                val objectPath = track.objectPath
                val myUid = auth.currentUser?.uid
                var serverNote = ""

                if (myUid != null) {
                    try {
                        if (transferId.isNotBlank()) {
                            val confirm = transferClient.confirm(
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
                            transferClient.deleteByPath(objectPath)
                            serverNote = " Файл удалён с сервера (по пути)."
                        } else {
                            serverNote =
                                " (нет transferId — файл удалится по таймеру ~10 мин)"
                        }
                    } catch (e: Exception) {
                        // Пробуем запасной путь
                        try {
                            if (objectPath.isNotBlank()) {
                                transferClient.deleteByPath(
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

                mainHandler.post {
                    try {
                        onSongReady(downloadedFile)
                        progress = 1f
                        progressText = ""
                        statusMessage = "Песня скачана.$serverNote"
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
        val wasAdmin = myRole == RoomRole.ADMIN
        val devicesSnapshot = connectedDevices.toList()

        removeRoomListeners()

        currentRoomCode = null
        roomCapacity = 0
        permissionMode = RoomPermissionMode.OPEN
        myRole = RoomRole.MEMBER
        adminUid = null
        roomHasPassword = false
        connectedDevices.clear()
        roomTracks.clear()
        progress = 0f
        progressText = ""

        latestPlaybackAuthor = null
        latestPlaybackRevision = null

        if (code != null && uid != null) {
            val roomRef = database.child("rooms").child(code)
            val deviceReference = roomRef.child("devices").child(uid)

            deviceReference.onDisconnect().cancel()

            if (wasAdmin) {
                transferAdminOnLeave(
                    roomRef = roomRef,
                    leavingUid = uid,
                    devices = devicesSnapshot
                ) {
                    deviceReference.removeValue()
                }
            } else {
                deviceReference.removeValue()
            }
        }

        statusMessage = "Вы вышли из комнаты."
    }

    /**
     * Админ назначает участника младшим админом (модератором).
     */
    fun promoteToModerator(targetUid: String) {
        val code = currentRoomCode ?: return
        val uid = auth.currentUser?.uid ?: return

        if (!canManageRoles()) {
            statusMessage = "Только админ может назначать младших админов."
            return
        }
        if (targetUid == uid) return

        val device = connectedDevices.find { it.id == targetUid }
        if (device == null) {
            statusMessage = "Участник не найден."
            return
        }
        if (device.role == RoomRole.ADMIN) {
            statusMessage = "Нельзя изменить роль админа."
            return
        }

        database.child("rooms")
            .child(code)
            .child("devices")
            .child(targetUid)
            .child("role")
            .setValue(RoomRole.MODERATOR.name)
            .addOnSuccessListener {
                statusMessage = "${device.name} теперь младший админ."
            }
            .addOnFailureListener { error ->
                statusMessage = "Не удалось назначить: ${error.message}"
            }
    }

    /**
     * Админ снимает права младшего админа.
     */
    fun demoteToMember(targetUid: String) {
        val code = currentRoomCode ?: return

        if (!canManageRoles()) {
            statusMessage = "Только админ может менять роли."
            return
        }

        val device = connectedDevices.find { it.id == targetUid }
        if (device == null || device.role != RoomRole.MODERATOR) {
            statusMessage = "Это не младший админ."
            return
        }

        database.child("rooms")
            .child(code)
            .child("devices")
            .child(targetUid)
            .child("role")
            .setValue(RoomRole.MEMBER.name)
            .addOnSuccessListener {
                statusMessage = "${device.name}: роль участника."
            }
            .addOnFailureListener { error ->
                statusMessage = "Не удалось снять роль: ${error.message}"
            }
    }

    private fun transferAdminOnLeave(
        roomRef: DatabaseReference,
        leavingUid: String,
        devices: List<RoomDevice>,
        onDone: () -> Unit
    ) {
        val candidatesMods = devices.filter {
            it.id != leavingUid && it.role == RoomRole.MODERATOR
        }
        val candidatesAny = devices.filter { it.id != leavingUid }

        val successor = when {
            candidatesMods.isNotEmpty() ->
                candidatesMods[random.nextInt(candidatesMods.size)]
            candidatesAny.isNotEmpty() ->
                candidatesAny[random.nextInt(candidatesAny.size)]
            else -> null
        }

        if (successor == null) {
            // Комната пустеет — можно удалить комнату целиком
            roomRef.removeValue().addOnCompleteListener { onDone() }
            return
        }

        val updates = hashMapOf<String, Any>(
            "info/adminUid" to successor.id,
            "devices/${successor.id}/role" to RoomRole.ADMIN.name
        )

        roomRef.updateChildren(updates)
            .addOnCompleteListener { onDone() }
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
        maxCapacity: Int,
        password: String,
        permissionMode: RoomPermissionMode,
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
                    findAvailableRoom(
                        uid, maxCapacity, password,
                        permissionMode, attemptsLeft - 1
                    )
                    return@addOnSuccessListener
                }

                val passwordHash = if (password.isNotBlank()) {
                    hashPassword(password)
                } else {
                    ""
                }

                val info = mutableMapOf<String, Any>(
                    "createdBy" to uid,
                    "createdAt" to System.currentTimeMillis(),
                    "maxCapacity" to maxCapacity,
                    "permissionMode" to permissionMode.name.lowercase(),
                    "adminUid" to uid
                )
                if (passwordHash.isNotBlank()) {
                    info["passwordHash"] = passwordHash
                }

                roomReference.child("info")
                    .setValue(info)
                    .addOnSuccessListener {
                        registerDevice(
                            code = code,
                            uid = uid,
                            maxCapacity = maxCapacity,
                            role = RoomRole.ADMIN,
                            permissionMode = permissionMode,
                            adminUidValue = uid,
                            hasPassword = passwordHash.isNotBlank()
                        )
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
        uid: String,
        maxCapacity: Int = DEFAULT_CAPACITY,
        role: RoomRole = RoomRole.MEMBER,
        permissionMode: RoomPermissionMode = RoomPermissionMode.OPEN,
        adminUidValue: String? = null,
        hasPassword: Boolean = false
    ) {
        val deviceReference = database
            .child("rooms")
            .child(code)
            .child("devices")
            .child(uid)

        // Если устройство уже было в комнате — сохраняем прежнюю роль
        deviceReference.get().addOnSuccessListener { existing ->
            val existingRole = if (existing.exists()) {
                RoomRole.fromString(
                    existing.child("role").getValue(String::class.java)
                )
            } else {
                role
            }

            // Создатель всегда админ при создании; при rejoin берём stored
            val finalRole = when {
                role == RoomRole.ADMIN -> RoomRole.ADMIN
                existing.exists() -> existingRole
                else -> RoomRole.MEMBER
            }

            val deviceData = mapOf(
                "name" to getDeviceName(),
                "joinedAt" to System.currentTimeMillis(),
                "role" to finalRole.name
            )

            deviceReference.onDisconnect()
                .removeValue()
                .addOnSuccessListener {
                    deviceReference.setValue(deviceData)
                        .addOnSuccessListener {
                            currentRoomCode = code
                            roomCapacity = maxCapacity
                            this.permissionMode = permissionMode
                            this.myRole = finalRole
                            this.adminUid = adminUidValue
                            this.roomHasPassword = hasPassword
                            isBusy = false
                            statusMessage =
                                "Вы подключены к комнате $code " +
                                        "(${finalRole.name}, " +
                                        "вместимость $maxCapacity)."
                            observeRoom(code)
                            worker.execute {
                                try {
                                    transferClient.cleanup()
                                } catch (_: Exception) {
                                }
                            }
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
        }.addOnFailureListener { error ->
            isBusy = false
            statusMessage = "Ошибка чтения устройства: ${error.message}"
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
                val myUid = auth.currentUser?.uid
                var seenAdmin = false

                for (child in snapshot.children) {
                    val id = child.key ?: continue

                    val name = child.child("name")
                        .getValue(String::class.java)
                        ?: "Устройство"

                    val role = RoomRole.fromString(
                        child.child("role").getValue(String::class.java)
                    )

                    if (role == RoomRole.ADMIN) {
                        seenAdmin = true
                        adminUid = id
                    }

                    if (id == myUid) {
                        myRole = role
                    }

                    connectedDevices.add(
                        RoomDevice(id, name, role)
                    )
                }

                // Админ отвалился без leaveRoom — выбираем преемника
                if (
                    !seenAdmin &&
                    connectedDevices.isNotEmpty() &&
                    currentRoomCode == code
                ) {
                    tryElectNewAdmin(code, snapshot)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                statusMessage =
                    "Ошибка получения устройств: ${error.message}"
            }
        }

        tracksListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val loaded = mutableListOf<Pair<Int, RoomTrack>>()

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

                    val transferId = child.child("transferId")
                        .getValue(String::class.java)
                        ?: ""

                    val objectPath = child.child("objectPath")
                        .getValue(String::class.java)
                        ?: ""

                    val order = (
                            child.child("order").value as? Number
                            )?.toInt()
                        ?: (
                                child.child("addedAt").value as? Number
                                )?.toInt()
                        ?: 0

                    loaded.add(
                        order to RoomTrack(
                            id = songId,
                            title = title,
                            downloadUrl = url,
                            addedBy = addedBy,
                            fileName = fileName,
                            transferId = transferId,
                            objectPath = objectPath
                        )
                    )
                }

                roomTracks.clear()
                loaded.sortedBy { it.first }.forEach {
                    roomTracks.add(it.second)
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

        // Следим за info (режим прав, adminUid)
        roomReference.child("info")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (currentRoomCode != code) return

                    permissionMode = RoomPermissionMode.fromString(
                        snapshot.child("permissionMode")
                            .getValue(String::class.java)
                    )
                    adminUid = snapshot.child("adminUid")
                        .getValue(String::class.java)
                    val hash = snapshot.child("passwordHash")
                        .getValue(String::class.java)
                        .orEmpty()
                    roomHasPassword = hash.isNotBlank()

                    val capacity = (
                            snapshot.child("maxCapacity").value as? Number
                            )?.toInt()
                    if (capacity != null) {
                        roomCapacity = capacity
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })

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

    /**
     * Если админ исчез из devices, любой клиент пытается
     * атомарно выбрать преемника (модератор → иначе любой).
     */
    private fun tryElectNewAdmin(code: String, devicesSnap: DataSnapshot) {
        val roomRef = database.child("rooms").child(code)
        val infoRef = roomRef.child("info")

        infoRef.runTransaction(object :
            com.google.firebase.database.Transaction.Handler {

            override fun doTransaction(
                currentData: com.google.firebase.database.MutableData
            ): com.google.firebase.database.Transaction.Result {
                val currentAdmin = currentData.child("adminUid")
                    .getValue(String::class.java)

                // Если админ всё ещё «числится» и он в devices — ничего не делаем.
                // Если adminUid указывает на отсутствующего — переназначаем.
                val adminStillPresent = currentAdmin != null &&
                        devicesSnap.child(currentAdmin).exists()

                if (adminStillPresent) {
                    return com.google.firebase.database.Transaction.success(
                        currentData
                    )
                }

                val mods = mutableListOf<String>()
                val all = mutableListOf<String>()
                for (child in devicesSnap.children) {
                    val id = child.key ?: continue
                    all.add(id)
                    val role = RoomRole.fromString(
                        child.child("role").getValue(String::class.java)
                    )
                    if (role == RoomRole.MODERATOR) {
                        mods.add(id)
                    }
                }

                if (all.isEmpty()) {
                    return com.google.firebase.database.Transaction.success(
                        currentData
                    )
                }

                val successor = if (mods.isNotEmpty()) {
                    mods[random.nextInt(mods.size)]
                } else {
                    all[random.nextInt(all.size)]
                }

                currentData.child("adminUid").value = successor
                return com.google.firebase.database.Transaction.success(
                    currentData
                )
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                if (!committed || error != null) return

                val newAdmin = currentData
                    ?.child("adminUid")
                    ?.getValue(String::class.java)
                    ?: return

                // Выставляем роль ADMIN преемнику
                roomRef.child("devices")
                    .child(newAdmin)
                    .child("role")
                    .setValue(RoomRole.ADMIN.name)

                // Снимаем ADMIN с остальных (на всякий случай)
                for (child in devicesSnap.children) {
                    val id = child.key ?: continue
                    if (id == newAdmin) continue
                    val role = RoomRole.fromString(
                        child.child("role").getValue(String::class.java)
                    )
                    if (role == RoomRole.ADMIN) {
                        roomRef.child("devices")
                            .child(id)
                            .child("role")
                            .setValue(RoomRole.MEMBER.name)
                    }
                }
            }
        })
    }

    private fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(
            password.trim().toByteArray(Charsets.UTF_8)
        )
        return bytes.joinToString("") { "%02x".format(it) }
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