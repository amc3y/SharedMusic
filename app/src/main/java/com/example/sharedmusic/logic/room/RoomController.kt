package com.example.sharedmusic.logic.room

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
import com.example.sharedmusic.logic.core.FirebaseConfig
import com.example.sharedmusic.logic.model.RemotePlaybackCommand
import com.example.sharedmusic.logic.model.RoomDevice
import com.example.sharedmusic.logic.model.RoomPermissionMode
import com.example.sharedmusic.logic.model.RoomRole
import com.example.sharedmusic.logic.model.RoomTrack
import com.example.sharedmusic.logic.model.Song
import com.example.sharedmusic.logic.room.playlist.PlaylistManager
import com.example.sharedmusic.logic.room.roles.RolesManager
import com.example.sharedmusic.logic.transfer.SupabaseStorage
import com.example.sharedmusic.logic.transfer.TransferApi
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

class RoomController(context: Context) {

    companion object {
        const val MIN_CAPACITY = FirebaseConfig.MIN_CAPACITY
        const val MAX_CAPACITY = FirebaseConfig.MAX_CAPACITY
        const val DEFAULT_CAPACITY = FirebaseConfig.DEFAULT_CAPACITY
    }

    internal val appContext = context.applicationContext
    internal val auth = FirebaseAuth.getInstance()

    internal val database = FirebaseDatabase
        .getInstance(FirebaseConfig.DATABASE_URL)
        .reference

    internal val storage = SupabaseStorage()
    internal val transferApi = TransferApi()
    private val playlist by lazy { PlaylistManager(this) }
    private val roles by lazy { RolesManager(this) }
    internal val random = java.security.SecureRandom()
    internal val worker = Executors.newSingleThreadExecutor()
    internal val mainHandler = Handler(Looper.getMainLooper())

    var currentRoomCode by mutableStateOf<String?>(null)
        internal set

    var statusMessage by mutableStateOf(
        "Создайте комнату или войдите по коду."
    )
        internal set

    var isBusy by mutableStateOf(false)
        internal set

    var roomCapacity by mutableStateOf(0)
        internal set

    var permissionMode by mutableStateOf(RoomPermissionMode.OPEN)
        internal set

    var myRole by mutableStateOf(RoomRole.MEMBER)
        internal set

    var adminUid by mutableStateOf<String?>(null)
        internal set

    var roomHasPassword by mutableStateOf(false)
        internal set

    var progress by mutableStateOf(0f)
        internal set

    var progressText by mutableStateOf("")
        internal set

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
            code.length != FirebaseConfig.ROOM_CODE_LENGTH ||
            code.any { it !in FirebaseConfig.ROOM_CHARACTERS }
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
    fun canControlPlayback(): Boolean {
        if (currentRoomCode == null) return false
        return when (permissionMode) {
            RoomPermissionMode.OPEN -> true
            RoomPermissionMode.ADMIN_ONLY -> myRole.canModerate()
        }
    }

    fun canShareMusic(): Boolean {
        if (currentRoomCode == null) return false
        return when (permissionMode) {
            RoomPermissionMode.OPEN -> true
            RoomPermissionMode.ADMIN_ONLY -> myRole.canModerate()
        }
    }

    fun canManageRoles(): Boolean {
        return currentRoomCode != null && myRole == RoomRole.ADMIN
    }

    fun shareSong(song: Song) = playlist.shareSong(song)

    fun shareSongs(
        songs: List<Song>,
        onFinished: ((Boolean, String) -> Unit)? = null
    ) = playlist.shareSongs(songs, onFinished)

    fun missingRoomTracks(localSongs: List<Song>) =
        playlist.missingRoomTracks(localSongs)

    fun hasAllRoomTracks(localSongs: List<Song>) =
        playlist.hasAllRoomTracks(localSongs)

    fun downloadMissingTracks(
        localSongs: List<Song>,
        onEachReady: (java.io.File, RoomTrack) -> Unit,
        onFinished: (Int, Int) -> Unit
    ) = playlist.downloadMissingTracks(localSongs, onEachReady, onFinished)

    fun downloadTrack(
        track: RoomTrack,
        onSongReady: (java.io.File) -> Unit
    ) = playlist.downloadTrack(track, onSongReady)


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

    internal fun writePlaybackState(
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

    fun promoteToModerator(targetUid: String) = roles.promoteToModerator(targetUid)

    fun demoteToMember(targetUid: String) = roles.demoteToMember(targetUid)

    internal fun transferAdminOnLeave(
        roomRef: DatabaseReference,
        leavingUid: String,
        devices: List<RoomDevice>,
        onDone: () -> Unit
    ) = roles.transferAdminOnLeave(roomRef, leavingUid, devices, onDone)

    internal fun tryElectNewAdmin(code: String, devicesSnap: DataSnapshot) =
        roles.tryElectNewAdmin(code, devicesSnap)

    internal fun hashPassword(password: String) = roles.hashPassword(password)

    fun dispose() {
        leaveRoom()
        playbackCallback = null

        serverTimeOffsetReference.removeEventListener(
            serverTimeOffsetListener
        )

        worker.shutdownNow()
    }

    internal fun ensureSignedIn(onReady: (String) -> Unit) {
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

    internal fun findAvailableRoom(
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

    internal fun registerDevice(
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
                                    transferApi.cleanup()
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

    internal fun observeRoom(code: String) {
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

    internal fun removeRoomListeners() {
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

    internal fun prepareSongFile(song: Song): Pair<File, String> {
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

    internal fun getDisplayName(
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

    internal fun generateRoomCode(): String {
        return buildString {
            repeat(FirebaseConfig.ROOM_CODE_LENGTH) {
                append(
                    FirebaseConfig.ROOM_CHARACTERS[
                        random.nextInt(FirebaseConfig.ROOM_CHARACTERS.length)
                    ]
                )
            }
        }
    }

    internal fun firebaseKey(value: String): String {
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

    internal fun safeFilePart(value: String): String {
        val result = value.map { char ->
            when (char) {
                '/', '\\', ':', '*', '?', '"', '<', '>', '|' -> '_'
                else -> char
            }
        }.joinToString("").trim()

        return result.ifBlank { "track" }
    }

    internal fun getExtension(fileName: String): String {
        val extension = fileName
            .substringAfterLast('.', "")
            .takeIf { it.isNotBlank() && it.length <= 10 }

        return if (extension == null) {
            ".audio"
        } else {
            ".$extension"
        }
    }

    internal fun getDeviceName(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}"
            .trim()
            .ifBlank { "Android device" }
    }

    internal fun reportProgress(
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

    internal fun formatBytes(bytes: Long): String {
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