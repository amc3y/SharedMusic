package com.example.sharedmusic.logic.room.roles

import com.example.sharedmusic.logic.model.RoomDevice
import com.example.sharedmusic.logic.model.RoomRole
import com.example.sharedmusic.logic.room.RoomController
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import java.security.MessageDigest

// Роли комнаты: назначение мл. админа, снятие, передача прав при выходе
class RolesManager(
    private val room: RoomController
) {
    fun promoteToModerator(targetUid: String) {
        val code = room.currentRoomCode ?: return
        val uid = room.auth.currentUser?.uid ?: return

        if (!room.canManageRoles()) {
            room.statusMessage = "Только админ может назначать младших админов."
            return
        }
        if (targetUid == uid) return

        val device = room.connectedDevices.find { it.id == targetUid }
        if (device == null) {
            room.statusMessage = "Участник не найден."
            return
        }
        if (device.role == RoomRole.ADMIN) {
            room.statusMessage = "Нельзя изменить роль админа."
            return
        }

        room.database.child("rooms")
            .child(code)
            .child("devices")
            .child(targetUid)
            .child("role")
            .setValue(RoomRole.MODERATOR.name)
            .addOnSuccessListener {
                room.statusMessage = "${device.name} теперь младший админ."
            }
            .addOnFailureListener { error ->
                room.statusMessage = "Не удалось назначить: ${error.message}"
            }
    }

    fun demoteToMember(targetUid: String) {
        val code = room.currentRoomCode ?: return

        if (!room.canManageRoles()) {
            room.statusMessage = "Только админ может менять роли."
            return
        }

        val device = room.connectedDevices.find { it.id == targetUid }
        if (device == null || device.role != RoomRole.MODERATOR) {
            room.statusMessage = "Это не младший админ."
            return
        }

        room.database.child("rooms")
            .child(code)
            .child("devices")
            .child(targetUid)
            .child("role")
            .setValue(RoomRole.MEMBER.name)
            .addOnSuccessListener {
                room.statusMessage = "${device.name}: роль участника."
            }
            .addOnFailureListener { error ->
                room.statusMessage = "Не удалось снять роль: ${error.message}"
            }
    }

    fun transferAdminOnLeave(
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
                candidatesMods[room.random.nextInt(candidatesMods.size)]
            candidatesAny.isNotEmpty() ->
                candidatesAny[room.random.nextInt(candidatesAny.size)]
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

    fun tryElectNewAdmin(code: String, devicesSnap: DataSnapshot) {
        val roomRef = room.database.child("rooms").child(code)
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
                    mods[room.random.nextInt(mods.size)]
                } else {
                    all[room.random.nextInt(all.size)]
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

    fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(
            password.trim().toByteArray(Charsets.UTF_8)
        )
        return bytes.joinToString("") { "%02x".format(it) }
    }


}
