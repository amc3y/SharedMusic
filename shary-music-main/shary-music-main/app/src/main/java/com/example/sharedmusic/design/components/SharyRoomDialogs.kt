package com.example.sharedmusic.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.sharedmusic.design.navigation.LocalUserProfile
import com.example.sharedmusic.design.room.RoomChatMessage
import com.example.sharedmusic.design.room.RoomExtendedController
import com.example.sharedmusic.design.theme.SharyThemeTokens
import com.example.sharedmusic.logic.model.RoomDevice
import com.example.sharedmusic.logic.model.RoomPermissionMode
import com.example.sharedmusic.logic.model.RoomRole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Универсальный кружок аватарки участника:
// - показывает загруженное фото пользователя или статичный серый логотип SHARY (если фото не загружено);
// - главный админ выделен сплошной линией, подадмин — пунктирной линией.
@Composable
fun SharyRoleAvatarRing(
    avatarBitmap: ImageBitmap?,
    role: RoomRole,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val surfaces = SharyThemeTokens.surfaces
    val ringColor = surfaces.textPrimary

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 2.0.dp.toPx()
            val radius = (this.size.minDimension - strokePx) / 2f
            when (role) {
                RoomRole.ADMIN -> {
                    drawCircle(
                        color = ringColor,
                        radius = radius,
                        style = Stroke(width = strokePx)
                    )
                }
                RoomRole.MODERATOR -> {
                    drawCircle(
                        color = ringColor.copy(alpha = 0.88f),
                        radius = radius,
                        style = Stroke(
                            width = strokePx,
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(11f, 7f),
                                0f
                            )
                        )
                    )
                }
                RoomRole.MEMBER -> {
                    drawCircle(
                        color = ringColor.copy(alpha = 0.18f),
                        radius = radius,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }
        }

        val innerSize = (size - 7.dp).coerceAtLeast(18.dp)
        Box(
            modifier = Modifier
                .size(innerSize)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (avatarBitmap != null) {
                Image(
                    bitmap = avatarBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                SharyDefaultGrayAvatarIcon(
                    backgroundTint = surfaces.placeholderCoverFill,
                    silhouetteTint = surfaces.placeholderLogoSecondary,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// Меню чата комнаты (открывается по значку чата в правом верхнем уголке 64.dp)
@Composable
fun SharyRoomChatDialog(
    roomCode: String,
    extendedRoom: RoomExtendedController,
    localProfile: LocalUserProfile,
    onDismiss: () -> Unit
) {
    val surfaces = SharyThemeTokens.surfaces
    var draftText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val messages = extendedRoom.chatMessages
    val myUid = extendedRoom.myUid
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(min = 340.dp, max = 560.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(surfaces.dialogFill)
                .border(1.dp, surfaces.dialogBorder, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SharyChatBubbleIcon(tint = surfaces.textPrimary, modifier = Modifier.size(20.dp))
                    Text(
                        text = "Чат комнаты #$roomCode",
                        color = surfaces.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(surfaces.navSelectionCircleSelected)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    SharySmallCrossIcon(tint = surfaces.textPrimary, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Сообщений пока нет. Напишите первым!",
                        color = surfaces.textSecondary,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(items = messages, key = { it.id }) { msg ->
                        ChatMessageRow(
                            message = msg,
                            isMine = msg.senderUid == myUid,
                            senderRole = extendedRoom.roomController.connectedDevices
                                .firstOrNull { it.id == msg.senderUid }?.role
                                ?: if (msg.senderUid == extendedRoom.roomController.adminUid) {
                                    RoomRole.ADMIN
                                } else {
                                    RoomRole.MEMBER
                                },
                            senderAvatar = if (msg.senderUid == myUid && localProfile.avatarBitmap != null) {
                                localProfile.avatarBitmap
                            } else {
                                extendedRoom.participantProfiles[msg.senderUid]?.avatarBitmap
                            },
                            formattedTime = if (msg.timestampMs > 0L) {
                                timeFormatter.format(Date(msg.timestampMs))
                            } else {
                                ""
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(surfaces.fieldFill)
                        .border(1.dp, surfaces.fieldBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (draftText.isEmpty()) {
                        Text(
                            text = "Написать сообщение...",
                            color = surfaces.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                    BasicTextField(
                        value = draftText,
                        onValueChange = { draftText = it.take(500) },
                        singleLine = true,
                        textStyle = TextStyle(color = surfaces.textPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(surfaces.textPrimary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (draftText.isNotBlank()) {
                                    extendedRoom.sendChatMessage(draftText, localProfile)
                                    draftText = ""
                                }
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                val canSend = draftText.isNotBlank()
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) surfaces.playerAccent
                            else surfaces.navSelectionCircleSelected
                        )
                        .clickable(enabled = canSend) {
                            extendedRoom.sendChatMessage(draftText, localProfile)
                            draftText = ""
                        },
                    contentAlignment = Alignment.Center
                ) {
                    SharySendMessageIcon(
                        tint = if (canSend) surfaces.onAccent else surfaces.textSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatMessageRow(
    message: RoomChatMessage,
    isMine: Boolean,
    senderRole: RoomRole,
    senderAvatar: ImageBitmap?,
    formattedTime: String
) {
    val surfaces = SharyThemeTokens.surfaces
    val isModOrAdmin = senderRole == RoomRole.ADMIN || senderRole == RoomRole.MODERATOR
    val bubbleFill = if (isModOrAdmin || isMine) {
        surfaces.songRowActiveFill
    } else {
        surfaces.songRowFill
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        SharyRoleAvatarRing(
            avatarBitmap = senderAvatar,
            role = senderRole,
            size = 38.dp
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(bubbleFill)
                .border(
                    width = 1.dp,
                    color = if (isModOrAdmin) surfaces.textPrimary.copy(alpha = 0.28f) else surfaces.songRowBorder,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (isMine) "${message.senderName} (Вы)" else message.senderName,
                        color = surfaces.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (senderRole == RoomRole.ADMIN || senderRole == RoomRole.MODERATOR) {
                        val badgeLabel = if (senderRole == RoomRole.ADMIN) "Админ" else "Подадмин"
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(surfaces.navSelectionCircleSelected)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = badgeLabel,
                                color = surfaces.textPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (formattedTime.isNotEmpty()) {
                    Text(
                        text = formattedTime,
                        color = surfaces.textSecondary,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = message.text,
                color = surfaces.textPrimary.copy(alpha = 0.94f),
                fontSize = 13.sp
            )
        }
    }
}

// Меню настроек комнаты при нажатии главного админа на самого себя:
// - заголовок «Настройки комнаты»;
// - поле «Новый пароль» + «Применить»;
// - «Управление музыкой»: «Админы» / «Права у всех»;
// - «Удалить всю комнату» (без вместимости и без кнопки отправки всех песен).
@Composable
fun SharyAdminSelfSettingsDialog(
    extendedRoom: RoomExtendedController,
    onRoomDeleted: () -> Unit,
    onDismiss: () -> Unit
) {
    val surfaces = SharyThemeTokens.surfaces
    val room = extendedRoom.roomController
    var newPassword by remember { mutableStateOf("") }
    var permissionMode by remember { mutableStateOf(room.permissionMode) }
    var confirmDeleteRoom by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(surfaces.dialogFill)
                .border(1.dp, surfaces.dialogBorder, RoundedCornerShape(16.dp))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Настройки комнаты",
                    color = surfaces.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(surfaces.navSelectionCircleSelected)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    SharySmallCrossIcon(tint = surfaces.textPrimary, modifier = Modifier.size(13.dp))
                }
            }

            // 1. Пароль комнаты
            Text(
                text = if (room.roomHasPassword) "Пароль комнаты: установлен" else "Пароль комнаты: без пароля",
                color = surfaces.textSecondary,
                fontSize = 12.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(surfaces.fieldFill)
                        .border(1.dp, surfaces.fieldBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (newPassword.isEmpty()) {
                        Text(
                            text = "Новый пароль",
                            color = surfaces.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                    BasicTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it.take(32) },
                        singleLine = true,
                        textStyle = TextStyle(color = surfaces.textPrimary, fontSize = 13.sp),
                        cursorBrush = SolidColor(surfaces.textPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                MonochromeActionPill(
                    text = "Применить",
                    filled = true,
                    onClick = {
                        extendedRoom.updateRoomPassword(newPassword)
                        newPassword = ""
                    }
                )
            }

            // 2. Режим управления музыкой («Админы» / «Права у всех»)
            Text(
                text = "Управление музыкой",
                color = surfaces.textSecondary,
                fontSize = 12.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MonochromeActionPill(
                    text = "Админы",
                    filled = permissionMode == RoomPermissionMode.ADMIN_ONLY,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        permissionMode = RoomPermissionMode.ADMIN_ONLY
                        extendedRoom.updateRoomPermissionMode(permissionMode)
                    }
                )
                MonochromeActionPill(
                    text = "Права у всех",
                    filled = permissionMode == RoomPermissionMode.OPEN,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        permissionMode = RoomPermissionMode.OPEN
                        extendedRoom.updateRoomPermissionMode(permissionMode)
                    }
                )
            }

            // 3. Удаление всей комнаты
            if (!confirmDeleteRoom) {
                MonochromeActionPill(
                    text = "Удалить всю комнату",
                    filled = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { confirmDeleteRoom = true }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(surfaces.songRowFill)
                        .border(1.dp, surfaces.dialogBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Точно удалить комнату для всех участников?",
                        color = surfaces.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MonochromeActionPill(
                            text = "Отмена",
                            filled = false,
                            modifier = Modifier.weight(1f),
                            onClick = { confirmDeleteRoom = false }
                        )
                        MonochromeActionPill(
                            text = "Удалить комнату",
                            filled = true,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                extendedRoom.deleteEntireRoom(onAfterLeave = onRoomDeleted)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

// Меню управления участником при нажатии админа или подадмина на кружок участника
@Composable
fun SharyManageParticipantDialog(
    targetDevice: RoomDevice,
    targetAvatar: ImageBitmap?,
    targetDisplayName: String,
    extendedRoom: RoomExtendedController,
    onDismiss: () -> Unit
) {
    val surfaces = SharyThemeTokens.surfaces
    val room = extendedRoom.roomController
    val isMainAdmin = room.myRole == RoomRole.ADMIN
    val canKick = extendedRoom.canKickParticipant(targetDevice)

    val roleTitle = when (targetDevice.role) {
        RoomRole.ADMIN -> "Главный админ"
        RoomRole.MODERATOR -> "Подадмин"
        RoomRole.MEMBER -> "Участник"
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(surfaces.dialogFill)
                .border(1.dp, surfaces.dialogBorder, RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SharyRoleAvatarRing(
                avatarBitmap = targetAvatar,
                role = targetDevice.role,
                size = 62.dp
            )

            Text(
                text = targetDisplayName,
                color = surfaces.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Текущая роль: $roleTitle",
                color = surfaces.textSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (isMainAdmin && targetDevice.role != RoomRole.ADMIN) {
                MonochromeActionPill(
                    text = "Отдать все права главного админа",
                    filled = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        extendedRoom.transferMainAdminRights(targetDevice)
                        onDismiss()
                    }
                )

                if (targetDevice.role == RoomRole.MEMBER) {
                    MonochromeActionPill(
                        text = "Сделать подадмином",
                        filled = false,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            room.promoteToModerator(targetDevice.id)
                            onDismiss()
                        }
                    )
                } else if (targetDevice.role == RoomRole.MODERATOR) {
                    MonochromeActionPill(
                        text = "Снять роль подадмина",
                        filled = false,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            room.demoteToMember(targetDevice.id)
                            onDismiss()
                        }
                    )
                }
            }

            if (canKick) {
                MonochromeActionPill(
                    text = "Удалить участника из комнаты",
                    filled = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        extendedRoom.kickParticipant(targetDevice)
                        onDismiss()
                    }
                )
            }

            MonochromeActionPill(
                text = "Закрыть",
                filled = false,
                modifier = Modifier.fillMaxWidth(),
                onClick = onDismiss
            )
        }
    }
}

// Быстрое подтверждение удаления участника при нажатии на маленькую мусорку справа снизу его аватарки
@Composable
fun SharyKickParticipantConfirmDialog(
    targetDevice: RoomDevice,
    targetAvatar: ImageBitmap?,
    targetDisplayName: String,
    extendedRoom: RoomExtendedController,
    onDismiss: () -> Unit
) {
    val surfaces = SharyThemeTokens.surfaces

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(surfaces.dialogFill)
                .border(1.dp, surfaces.dialogBorder, RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SharyRoleAvatarRing(
                avatarBitmap = targetAvatar,
                role = targetDevice.role,
                size = 56.dp
            )

            Text(
                text = "Удалить из комнаты?",
                color = surfaces.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "Участник «$targetDisplayName» будет отключён от комнаты.",
                color = surfaces.textSecondary,
                fontSize = 13.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MonochromeActionPill(
                    text = "Отмена",
                    filled = false,
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss
                )
                MonochromeActionPill(
                    text = "Удалить",
                    filled = true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        extendedRoom.kickParticipant(targetDevice)
                        onDismiss()
                    }
                )
            }
        }
    }
}

// Меню подтверждения выхода из комнаты
@Composable
fun SharyLeaveRoomConfirmDialog(
    extendedRoom: RoomExtendedController,
    onConfirmedLeave: () -> Unit,
    onDismiss: () -> Unit
) {
    val surfaces = SharyThemeTokens.surfaces
    val room = extendedRoom.roomController
    val myUid = extendedRoom.myUid
    val isMainAdmin = room.myRole == RoomRole.ADMIN
    val otherParticipants = remember(room.connectedDevices.toList(), myUid) {
        room.connectedDevices
            .filter { it.id != myUid }
            .sortedBy { if (it.role == RoomRole.MODERATOR) 0 else 1 }
    }
    var selectedSuccessorUid by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(surfaces.dialogFill)
                .border(1.dp, surfaces.dialogBorder, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Вы точно хотите выйти из комнаты?",
                color = surfaces.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (isMainAdmin && otherParticipants.isNotEmpty()) {
                val hasSubAdmins = otherParticipants.any { it.role == RoomRole.MODERATOR }
                Text(
                    text = if (hasSubAdmins) {
                        "Вы главный админ. При выходе права автоматически перейдут случайному подадмину (или выберите преемника вручную):"
                    } else {
                        "Вы главный админ. Подадминов нет — права автоматически перейдут случайному участнику (или выберите преемника вручную):"
                    },
                    color = surfaces.textSecondary,
                    fontSize = 12.sp
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MonochromeActionPill(
                        text = if (hasSubAdmins) "Автоматически (случайному подадмину)" else "Автоматически (случайному участнику)",
                        filled = selectedSuccessorUid == null,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { selectedSuccessorUid = null }
                    )

                    otherParticipants.take(5).forEach { candidate ->
                        val tag = if (candidate.role == RoomRole.MODERATOR) " • Подадмин" else ""
                        MonochromeActionPill(
                            text = "${extendedRoom.resolveDisplayName(candidate)}$tag",
                            filled = selectedSuccessorUid == candidate.id,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { selectedSuccessorUid = candidate.id }
                        )
                    }
                }
            } else if (isMainAdmin && otherParticipants.isEmpty()) {
                Text(
                    text = "Вы единственный участник в комнате — после выхода комната будет закрыта.",
                    color = surfaces.textSecondary,
                    fontSize = 12.sp
                )
            } else {
                Text(
                    text = "Совместное прослушивание в комнате #${room.currentRoomCode.orEmpty()} будет остановлено.",
                    color = surfaces.textSecondary,
                    fontSize = 12.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MonochromeActionPill(
                    text = "Остаться",
                    filled = false,
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss
                )
                MonochromeActionPill(
                    text = "Выйти",
                    filled = true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        extendedRoom.leaveRoomCleanly(
                            explicitSuccessorUid = selectedSuccessorUid,
                            onAfterLeave = onConfirmedLeave
                        )
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
internal fun MonochromeActionPill(
    text: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val surfaces = SharyThemeTokens.surfaces
    val shape = RoundedCornerShape(10.dp)
    val bg = when {
        !enabled -> surfaces.songRowFill.copy(alpha = 0.4f)
        filled -> surfaces.playerAccent
        else -> surfaces.fieldFill
    }
    val border = if (filled) {
        surfaces.textPrimary.copy(alpha = 0.26f)
    } else {
        surfaces.fieldBorder
    }
    val textColor = when {
        !enabled -> surfaces.textSecondary.copy(alpha = 0.5f)
        filled -> surfaces.onAccent
        else -> surfaces.textPrimary
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
