package com.example.sharedmusic.design.room

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.example.sharedmusic.design.navigation.LocalUserProfile
import com.example.sharedmusic.design.navigation.UserProfileStore
import com.example.sharedmusic.logic.model.RoomDevice
import com.example.sharedmusic.logic.model.RoomPermissionMode
import com.example.sharedmusic.logic.model.RoomRole
import com.example.sharedmusic.logic.room.RoomController
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

data class ParticipantProfileInfo(
    val uid: String,
    val displayName: String = "",
    val avatarBase64: String = "",
    val avatarBitmap: ImageBitmap? = null
)

data class RoomChatMessage(
    val id: String,
    val senderUid: String,
    val senderName: String,
    val text: String,
    val timestampMs: Long
)

// Расширенный контроллер функций комнаты (аватарки участников, самовосстановление присутствия, чат, кик, права админов, смена пароля, удаление комнаты)
// Работает поверх RoomController партнёра через общую Firebase БД без единого изменения файлов в logic/
class RoomExtendedController(
    val roomController: RoomController
) {
    val participantProfiles = mutableStateMapOf<String, ParticipantProfileInfo>()
    val chatMessages = mutableStateListOf<RoomChatMessage>()

    var unreadChatCount by mutableIntStateOf(0)
        private set

    var isChatOpen by mutableStateOf(false)
        private set

    var toastNotice by mutableStateOf<String?>(null)

    private var observedCode: String? = null
    private var profilesRef: DatabaseReference? = null
    private var profilesListener: ValueEventListener? = null

    private var chatRef: DatabaseReference? = null
    private var chatListener: ValueEventListener? = null
    private var initialChatLoaded = false
    private var lastSeenMessageCount = 0

    private var kickRef: DatabaseReference? = null
    private var kickListener: ValueEventListener? = null

    private var roomInfoWatchRef: DatabaseReference? = null
    private var roomInfoWatchListener: ValueEventListener? = null
    private var seenRoomInfoOnce = false

    private var selfDeviceWatchRef: DatabaseReference? = null
    private var selfDeviceWatchListener: ValueEventListener? = null
    private var connectedWatchRef: DatabaseReference? = null
    private var connectedWatchListener: ValueEventListener? = null
    private var latestLocalProfileSnapshot: LocalUserProfile = LocalUserProfile()

    val myUid: String?
        get() = roomController.auth.currentUser?.uid

    // Главный админ ВСЕГДА располагается первым слева, далее подадмины, далее обычные участники.
    // Гарантирует присутствие самого пользователя, пока он находится в активной комнате (полоса участников никогда не пустеет!).
    fun sortedParticipants(localProfile: LocalUserProfile = latestLocalProfileSnapshot): List<RoomDevice> {
        val rawDevices = roomController.connectedDevices.toList()
        val currentCode = roomController.currentRoomCode
        val currentUid = myUid

        val merged = if (currentCode != null) {
            val selfKey = currentUid ?: "local_self"
            val hasSelf = rawDevices.any { it.id == selfKey }
            if (hasSelf) {
                rawDevices
            } else {
                rawDevices + RoomDevice(
                    id = selfKey,
                    name = localProfile.displayName.ifBlank { roomController.getDeviceName() },
                    role = roomController.myRole
                )
            }
        } else {
            rawDevices
        }

        return merged.sortedWith(
            compareBy<RoomDevice> { device ->
                when (device.role) {
                    RoomRole.ADMIN -> 0
                    RoomRole.MODERATOR -> 1
                    RoomRole.MEMBER -> 2
                }
            }.thenBy { it.name.lowercase() }
        )
    }

    fun canKickParticipant(target: RoomDevice): Boolean {
        val currentUid = myUid ?: return false
        if (target.id == currentUid) return false
        if (target.role == RoomRole.ADMIN) return false
        return when (roomController.myRole) {
            RoomRole.ADMIN -> true
            RoomRole.MODERATOR -> target.role == RoomRole.MEMBER
            RoomRole.MEMBER -> false
        }
    }

    fun openChat() {
        isChatOpen = true
        unreadChatCount = 0
        lastSeenMessageCount = chatMessages.size
    }

    fun closeChat() {
        isChatOpen = false
        unreadChatCount = 0
        lastSeenMessageCount = chatMessages.size
    }

    fun publishOwnProfile(localProfile: LocalUserProfile) {
        latestLocalProfileSnapshot = localProfile
        val code = roomController.currentRoomCode ?: return
        val uid = myUid ?: return
        val ref = roomController.database
            .child("rooms")
            .child(code)
            .child("profiles")
            .child(uid)

        val payload = mapOf(
            "displayName" to localProfile.displayName,
            "avatarBase64" to localProfile.avatarBase64,
            "updatedAt" to ServerValue.TIMESTAMP
        )
        ref.onDisconnect().removeValue()
        ref.setValue(payload)
    }

    // Восстанавливает запись устройства в Firebase после кратковременного обрыва сети (например, при сворачивании приложения или шаринге кода)
    private fun ensureOwnPresenceRestored(code: String) {
        if (roomController.currentRoomCode != code) return
        val uid = myUid ?: return
        val roomRef = roomController.database.child("rooms").child(code)

        roomRef.child("info").get().addOnSuccessListener { infoSnap ->
            if (!infoSnap.exists() || roomController.currentRoomCode != code) return@addOnSuccessListener
            if (infoSnap.child("deleted").getValue(Boolean::class.java) == true) return@addOnSuccessListener

            roomRef.child("kicked").child(uid).get().addOnSuccessListener { kickSnap ->
                if (kickSnap.exists() || roomController.currentRoomCode != code) return@addOnSuccessListener

                val devRef = roomRef.child("devices").child(uid)
                devRef.get().addOnSuccessListener { devSnap ->
                    if (!devSnap.exists() && roomController.currentRoomCode == code) {
                        val adminInRoom = infoSnap.child("adminUid").getValue(String::class.java)
                        val roleToRestore = if (adminInRoom == uid) {
                            RoomRole.ADMIN
                        } else {
                            roomController.myRole
                        }
                        val payload = mapOf(
                            "name" to roomController.getDeviceName(),
                            "joinedAt" to System.currentTimeMillis(),
                            "role" to roleToRestore.name
                        )
                        devRef.onDisconnect().removeValue()
                        devRef.setValue(payload)
                        publishOwnProfile(latestLocalProfileSnapshot)
                    }
                }
            }
        }
    }

    fun sendChatMessage(rawText: String, localProfile: LocalUserProfile) {
        val code = roomController.currentRoomCode ?: return
        val uid = myUid ?: return
        val trimmed = rawText.trim().take(500)
        if (trimmed.isEmpty()) return

        val myDeviceName = roomController.connectedDevices
            .firstOrNull { it.id == uid }
            ?.name
            .orEmpty()
        val displaySender = localProfile.displayName.ifBlank {
            myDeviceName.ifBlank { "Участник" }
        }

        val msgRef = roomController.database
            .child("rooms")
            .child(code)
            .child("chat")
            .push()

        val payload = mapOf(
            "senderUid" to uid,
            "senderName" to displaySender,
            "text" to trimmed,
            "timestamp" to ServerValue.TIMESTAMP
        )
        msgRef.setValue(payload)
    }

    // Удаление (кик) участника админом или подадмином
    fun kickParticipant(target: RoomDevice) {
        val code = roomController.currentRoomCode ?: return
        if (!canKickParticipant(target)) {
            toastNotice = "Удалять участников могут только админы"
            return
        }

        val roomRef = roomController.database.child("rooms").child(code)
        roomRef.child("kicked").child(target.id).setValue(ServerValue.TIMESTAMP)
        roomRef.child("devices").child(target.id).removeValue()
        roomRef.child("profiles").child(target.id).removeValue()
        toastNotice = "${resolveDisplayName(target)} удалён из комнаты"
    }

    // Полная передача прав главного админа другому участнику
    fun transferMainAdminRights(target: RoomDevice) {
        val code = roomController.currentRoomCode ?: return
        val uid = myUid ?: return
        if (roomController.myRole != RoomRole.ADMIN || target.id == uid) return

        val updates = hashMapOf<String, Any>(
            "info/adminUid" to target.id,
            "devices/${target.id}/role" to RoomRole.ADMIN.name,
            "devices/$uid/role" to RoomRole.MEMBER.name
        )
        roomController.database
            .child("rooms")
            .child(code)
            .updateChildren(updates)
            .addOnSuccessListener {
                toastNotice = "Права главного админа переданы: ${resolveDisplayName(target)}"
            }
            .addOnFailureListener { e ->
                toastNotice = "Ошибка передачи прав: ${e.message}"
            }
    }

    // Изменение или сброс пароля комнаты главным админом
    fun updateRoomPassword(newPassword: String) {
        val code = roomController.currentRoomCode ?: return
        if (roomController.myRole != RoomRole.ADMIN) return

        val infoRef = roomController.database.child("rooms").child(code).child("info")
        val trimmed = newPassword.trim()
        if (trimmed.isEmpty()) {
            infoRef.child("passwordHash").removeValue().addOnSuccessListener {
                toastNotice = "Пароль комнаты убран"
            }
        } else {
            val hash = roomController.hashPassword(trimmed)
            infoRef.child("passwordHash").setValue(hash).addOnSuccessListener {
                toastNotice = "Пароль комнаты обновлён"
            }
        }
    }

    // Обновление режима прав управления музыкой главным админом (без лимита вместимости)
    fun updateRoomPermissionMode(permissionMode: RoomPermissionMode) {
        val code = roomController.currentRoomCode ?: return
        if (roomController.myRole != RoomRole.ADMIN) return

        val updates = mapOf<String, Any>(
            "permissionMode" to permissionMode.name.lowercase(),
            "maxCapacity" to RoomController.MAX_CAPACITY
        )
        roomController.database
            .child("rooms")
            .child(code)
            .child("info")
            .updateChildren(updates)
            .addOnSuccessListener {
                toastNotice = "Права управления сохранены"
            }
    }

    // Полное удаление всей комнаты (доступно ТОЛЬКО главному админу)
    fun deleteEntireRoom(onAfterLeave: () -> Unit = {}) {
        val code = roomController.currentRoomCode ?: return
        if (roomController.myRole != RoomRole.ADMIN) {
            toastNotice = "Удалить комнату может только главный админ"
            return
        }

        val roomRef = roomController.database.child("rooms").child(code)
        detachExtendedListeners()
        roomController.leaveRoom()
        roomRef.child("info").child("deleted").setValue(true)
        roomRef.removeValue()
        onAfterLeave()
        toastNotice = "Комната удалена"
    }

    // Выход из комнаты (если админ выбрал конкретного преемника или оставил автоназначение подадмину/случайному)
    fun leaveRoomCleanly(
        explicitSuccessorUid: String? = null,
        onAfterLeave: () -> Unit = {}
    ) {
        val code = roomController.currentRoomCode
        val uid = myUid
        if (
            code != null &&
            uid != null &&
            roomController.myRole == RoomRole.ADMIN &&
            !explicitSuccessorUid.isNullOrBlank() &&
            explicitSuccessorUid != uid
        ) {
            val roomRef = roomController.database.child("rooms").child(code)
            val updates = hashMapOf<String, Any?>(
                "info/adminUid" to explicitSuccessorUid,
                "devices/$explicitSuccessorUid/role" to RoomRole.ADMIN.name,
                "devices/$uid/role" to RoomRole.MEMBER.name
            )
            roomRef.updateChildren(updates).addOnCompleteListener {
                roomRef.child("profiles").child(uid).removeValue()
                detachExtendedListeners()
                roomController.leaveRoom()
                onAfterLeave()
            }
        } else {
            if (code != null && uid != null) {
                roomController.database
                    .child("rooms")
                    .child(code)
                    .child("profiles")
                    .child(uid)
                    .removeValue()
            }
            detachExtendedListeners()
            roomController.leaveRoom()
            onAfterLeave()
        }
    }

    fun resolveDisplayName(device: RoomDevice): String {
        val custom = participantProfiles[device.id]?.displayName.orEmpty()
        return custom.ifBlank { device.name }
    }

    internal fun attachToRoom(
        code: String,
        localProfile: LocalUserProfile,
        onForcedExit: (String) -> Unit
    ) {
        latestLocalProfileSnapshot = localProfile
        if (observedCode == code) return
        detachExtendedListeners()
        observedCode = code
        seenRoomInfoOnce = false
        initialChatLoaded = false
        lastSeenMessageCount = 0
        unreadChatCount = 0

        val roomRef = roomController.database.child("rooms").child(code)
        val uid = myUid

        if (uid != null) {
            roomRef.child("kicked").child(uid).removeValue()
        }

        publishOwnProfile(localProfile)

        // 1. Слушаем профили участников (аватарки и имена)
        val pRef = roomRef.child("profiles")
        val pListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val activeKeys = HashSet<String>()
                for (child in snapshot.children) {
                    val memberUid = child.key ?: continue
                    activeKeys.add(memberUid)
                    val dispName = child.child("displayName").getValue(String::class.java).orEmpty()
                    val b64 = child.child("avatarBase64").getValue(String::class.java).orEmpty()
                    val bmp = UserProfileStore.decodeAvatarBase64(b64)
                    participantProfiles[memberUid] = ParticipantProfileInfo(
                        uid = memberUid,
                        displayName = dispName,
                        avatarBase64 = b64,
                        avatarBitmap = bmp
                    )
                }
                val toRemove = participantProfiles.keys.filter { it !in activeKeys }
                toRemove.forEach { participantProfiles.remove(it) }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        profilesRef = pRef
        profilesListener = pListener
        pRef.addValueEventListener(pListener)

        // 2. Слушаем чат комнаты
        val cRef = roomRef.child("chat")
        val cListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val loaded = ArrayList<RoomChatMessage>()
                for (child in snapshot.children) {
                    val id = child.key ?: continue
                    val senderUid = child.child("senderUid").getValue(String::class.java).orEmpty()
                    val senderName = child.child("senderName").getValue(String::class.java) ?: "Участник"
                    val text = child.child("text").getValue(String::class.java) ?: continue
                    val ts = (child.child("timestamp").value as? Number)?.toLong() ?: 0L
                    loaded.add(
                        RoomChatMessage(
                            id = id,
                            senderUid = senderUid,
                            senderName = senderName,
                            text = text,
                            timestampMs = ts
                        )
                    )
                }
                loaded.sortBy { it.timestampMs }
                val capped = if (loaded.size > 120) loaded.takeLast(120) else loaded

                chatMessages.clear()
                chatMessages.addAll(capped)

                if (!initialChatLoaded) {
                    initialChatLoaded = true
                    lastSeenMessageCount = capped.size
                } else if (!isChatOpen && capped.size > lastSeenMessageCount) {
                    val newIncoming = capped
                        .drop(lastSeenMessageCount)
                        .count { it.senderUid != myUid }
                    unreadChatCount += newIncoming
                    lastSeenMessageCount = capped.size
                } else {
                    lastSeenMessageCount = capped.size
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        chatRef = cRef
        chatListener = cListener
        cRef.addValueEventListener(cListener)

        // 3. Слушаем принудительное удаление (кик) и самовосстанавливаем присутствие после сетевых скачков
        if (uid != null) {
            val kRef = roomRef.child("kicked").child(uid)
            val kListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists() && roomController.currentRoomCode == code) {
                        kRef.removeValue()
                        detachExtendedListeners()
                        roomController.leaveRoom()
                        toastNotice = "Вы были удалены из комнаты"
                        onForcedExit("Вы были удалены из комнаты")
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            }
            kickRef = kRef
            kickListener = kListener
            kRef.addValueEventListener(kListener)

            var sawOwnDeviceOnce = false
            val selfDevRef = roomRef.child("devices").child(uid)
            val selfDevListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (roomController.currentRoomCode != code) return
                    if (snapshot.exists()) {
                        sawOwnDeviceOnce = true
                    } else if (sawOwnDeviceOnce) {
                        ensureOwnPresenceRestored(code)
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            }
            selfDeviceWatchRef = selfDevRef
            selfDeviceWatchListener = selfDevListener
            selfDevRef.addValueEventListener(selfDevListener)

            val connRef = roomController.database.child(".info").child("connected")
            val connListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val connected = snapshot.getValue(Boolean::class.java) == true
                    if (connected && roomController.currentRoomCode == code) {
                        ensureOwnPresenceRestored(code)
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            }
            connectedWatchRef = connRef
            connectedWatchListener = connListener
            connRef.addValueEventListener(connListener)
        }

        // 4. Слушаем полное удаление комнаты главным админом
        val infoRef = roomRef.child("info")
        val infoListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (roomController.currentRoomCode != code) return
                val deletedFlag = snapshot.child("deleted").getValue(Boolean::class.java) == true
                if (snapshot.exists() && !deletedFlag) {
                    seenRoomInfoOnce = true
                } else if (deletedFlag || (seenRoomInfoOnce && !snapshot.exists())) {
                    detachExtendedListeners()
                    roomController.leaveRoom()
                    toastNotice = "Комната была закрыта администратором"
                    onForcedExit("Комната была закрыта администратором")
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        roomInfoWatchRef = infoRef
        roomInfoWatchListener = infoListener
        infoRef.addValueEventListener(infoListener)
    }

    internal fun detachExtendedListeners() {
        profilesListener?.let { profilesRef?.removeEventListener(it) }
        chatListener?.let { chatRef?.removeEventListener(it) }
        kickListener?.let { kickRef?.removeEventListener(it) }
        roomInfoWatchListener?.let { roomInfoWatchRef?.removeEventListener(it) }
        selfDeviceWatchListener?.let { selfDeviceWatchRef?.removeEventListener(it) }
        connectedWatchListener?.let { connectedWatchRef?.removeEventListener(it) }

        profilesListener = null
        chatListener = null
        kickListener = null
        roomInfoWatchListener = null
        selfDeviceWatchListener = null
        connectedWatchListener = null

        profilesRef = null
        chatRef = null
        kickRef = null
        roomInfoWatchRef = null
        selfDeviceWatchRef = null
        connectedWatchRef = null
        observedCode = null

        participantProfiles.clear()
        chatMessages.clear()
        unreadChatCount = 0
        isChatOpen = false
    }
}

@Composable
fun rememberRoomExtendedController(
    roomController: RoomController,
    localProfile: LocalUserProfile,
    onForcedExit: (String) -> Unit = {}
): RoomExtendedController {
    val ext = remember(roomController) { RoomExtendedController(roomController) }
    val roomCode = roomController.currentRoomCode
    val latestOnForcedExit by rememberUpdatedState(onForcedExit)

    LaunchedEffect(roomCode) {
        if (roomCode != null) {
            ext.attachToRoom(
                code = roomCode,
                localProfile = localProfile,
                onForcedExit = { msg -> latestOnForcedExit(msg) }
            )
        } else {
            ext.detachExtendedListeners()
        }
    }

    LaunchedEffect(roomCode, localProfile.displayName, localProfile.avatarBase64) {
        if (roomCode != null) {
            ext.publishOwnProfile(localProfile)
        }
    }

    DisposableEffect(ext) {
        onDispose {
            ext.detachExtendedListeners()
        }
    }

    return ext
}
