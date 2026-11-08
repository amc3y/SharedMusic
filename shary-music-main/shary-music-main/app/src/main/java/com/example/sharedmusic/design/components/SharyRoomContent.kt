package com.example.sharedmusic.design.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sharedmusic.R
import com.example.sharedmusic.design.navigation.LocalUserProfile
import com.example.sharedmusic.design.navigation.SongDuplicateGuard
import com.example.sharedmusic.design.room.RoomExtendedController
import com.example.sharedmusic.design.theme.SharyDimens
import com.example.sharedmusic.design.theme.SharyMilk
import com.example.sharedmusic.design.theme.SharyThemeTokens
import com.example.sharedmusic.logic.core.FirebaseConfig
import com.example.sharedmusic.logic.model.RoomDevice
import com.example.sharedmusic.logic.model.RoomPermissionMode
import com.example.sharedmusic.logic.model.RoomRole
import com.example.sharedmusic.logic.model.RoomTrack
import com.example.sharedmusic.logic.model.Song
import com.example.sharedmusic.logic.room.RoomController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// Элемент вертикального слайдера мини-пластинок справа в Комнате
private data class RoomBrowsePlate(
    val id: String,
    val title: String,
    val roomTrack: RoomTrack?,
    val localSong: Song?
)

// Очищает системные сообщения RoomController партнёра от любых упоминаний вместимости/мест
private fun sanitizeRoomStatusMessage(raw: String): String {
    if (raw.isBlank()) return ""
    return raw
        .replace(Regex("\\s*\\(до\\s*\\d+\\s*чел\\.\\)"), "")
        .replace(Regex(",\\s*вместимость\\s*\\d+"), "")
        .replace(Regex("\\s*\\(\\d+/\\d+\\)"), "")
        .trim()
}

// Верхняя полоса участников комнаты (между 1-й верхней и 2-й средней областями):
// - никогда не пустеет в активной комнате (гарантирует присутствие самого пользователя и авто-восстановление после скачков сети);
// - главный админ всегда крайний слева (сплошная линия);
// - подадмин выделен пунктирной линией;
// - загруженная аватарка пользователя или статичный серый логотип SHARY (если фото нет).
@Composable
fun SharyRoomParticipantsStrip(
    extendedRoom: RoomExtendedController,
    localProfile: LocalUserProfile,
    onParticipantAvatarClick: (RoomDevice) -> Unit,
    onParticipantKickBadgeClick: (RoomDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    val surfaces = SharyThemeTokens.surfaces
    val participants = extendedRoom.sortedParticipants(localProfile)
    val myUid = extendedRoom.myUid ?: "local_self"

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .height(SharyDimens.RoomParticipantsStripHeight),
        contentPadding = PaddingValues(horizontal = SharyDimens.ScreenOuterMargin + 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(
            items = participants,
            key = { it.id }
        ) { device ->
            val isMe = device.id == myUid || device.id == "local_self"
            val avatarBmp: ImageBitmap? = if (isMe && localProfile.avatarBitmap != null) {
                localProfile.avatarBitmap
            } else {
                extendedRoom.participantProfiles[device.id]?.avatarBitmap
            }

            val baseName = if (isMe) {
                localProfile.displayName.ifBlank { "Вы" }
            } else {
                extendedRoom.resolveDisplayName(device)
            }

            val labelText = when {
                isMe && device.role == RoomRole.ADMIN ->
                    if (localProfile.displayName.isBlank()) "Вы (Хост)" else "$baseName (Хост)"
                isMe && device.role == RoomRole.MODERATOR ->
                    if (localProfile.displayName.isBlank()) "Вы (Подадмин)" else "$baseName (Подадмин)"
                else -> baseName
            }

            val showAdminSelfBadge = isMe && device.role == RoomRole.ADMIN
            val showTrashBadge = !isMe && device.role != RoomRole.ADMIN

            Column(
                modifier = Modifier.width(78.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(SharyDimens.RoomParticipantAvatarSize + 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SharyRoleAvatarRing(
                        avatarBitmap = avatarBmp,
                        role = device.role,
                        size = SharyDimens.RoomParticipantAvatarSize,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onParticipantAvatarClick(device) }
                    )

                    if (showAdminSelfBadge) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(SharyDimens.RoomParticipantBadgeSize)
                                .clip(CircleShape)
                                .background(surfaces.playerAccent)
                                .border(1.dp, surfaces.onAccent.copy(alpha = 0.32f), CircleShape)
                                .clickable { onParticipantAvatarClick(device) },
                            contentAlignment = Alignment.Center
                        ) {
                            SharySettingsIcon(
                                tint = surfaces.onAccent,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    } else if (showTrashBadge) {
                        val canKick = extendedRoom.canKickParticipant(device)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(SharyDimens.RoomParticipantBadgeSize)
                                .clip(CircleShape)
                                .background(
                                    if (canKick) surfaces.playerAccent
                                    else surfaces.playerAccent.copy(alpha = 0.55f)
                                )
                                .border(1.dp, surfaces.onAccent.copy(alpha = 0.26f), CircleShape)
                                .clickable { onParticipantKickBadgeClick(device) },
                            contentAlignment = Alignment.Center
                        ) {
                            SharyTrashIcon(
                                tint = if (canKick) surfaces.onAccent else surfaces.onAccent.copy(alpha = 0.55f),
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0.0f to Color.Black,
                                    0.78f to Color.Black,
                                    1.0f to Color.Transparent
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labelText,
                        color = surfaces.textPrimary.copy(
                            alpha = if (isMe || device.role == RoomRole.ADMIN) 0.92f else 0.76f
                        ),
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// Содержимое 2-й средней области на вкладке «Комната»
@Composable
fun SharyRoomMiddleContent(
    connectionManager: RoomController,
    extendedRoom: RoomExtendedController,
    allLoadedSongs: List<Song>,
    userLibrarySongs: List<Song>,
    currentSongId: String?,
    favoriteSongIds: Set<String>,
    isPlaying: Boolean,
    positionMsFlow: StateFlow<Long>,
    durationMsFlow: StateFlow<Long>,
    onPlayRoomSong: (Song) -> Unit,
    onDownloadAndPlayRoomTrack: (RoomTrack) -> Unit,
    onShareSongToRoom: (Song) -> Unit,
    onAddNewSongsToRoomClick: () -> Unit,
    onSeekRoomSong: (Song, Long) -> Unit,
    onSaveRoomTrackToLibrary: (RoomTrack?, Song?) -> Unit,
    onToggleFavoriteSong: (String) -> Unit,
    onOpenChatClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val surfaces = SharyThemeTokens.surfaces
    val inRoom = connectionManager.currentRoomCode != null

    Box(modifier = modifier.fillMaxSize()) {
        when {
            !inRoom && connectionManager.isBusy -> {
                SharyRoomLoadingView(
                    statusMessage = sanitizeRoomStatusMessage(connectionManager.statusMessage)
                )
            }
            !inRoom -> {
                SharyRoomLobbyView(
                    connectionManager = connectionManager
                )
            }
            else -> {
                SharyActiveRoomPlayerView(
                    connectionManager = connectionManager,
                    extendedRoom = extendedRoom,
                    allLoadedSongs = allLoadedSongs,
                    userLibrarySongs = userLibrarySongs,
                    currentSongId = currentSongId,
                    favoriteSongIds = favoriteSongIds,
                    isPlaying = isPlaying,
                    positionMsFlow = positionMsFlow,
                    durationMsFlow = durationMsFlow,
                    onPlayRoomSong = onPlayRoomSong,
                    onDownloadAndPlayRoomTrack = onDownloadAndPlayRoomTrack,
                    onShareSongToRoom = onShareSongToRoom,
                    onAddNewSongsToRoomClick = onAddNewSongsToRoomClick,
                    onSeekRoomSong = onSeekRoomSong,
                    onSaveRoomTrackToLibrary = onSaveRoomTrackToLibrary,
                    onToggleFavoriteSong = onToggleFavoriteSong,
                    onOpenChatClick = onOpenChatClick
                )
            }
        }

        val notice = extendedRoom.toastNotice
        if (notice != null) {
            LaunchedEffect(notice) {
                delay(2_600L)
                if (extendedRoom.toastNotice == notice) {
                    extendedRoom.toastNotice = null
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(surfaces.dialogFill)
                    .border(1.dp, surfaces.dialogBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = notice,
                    color = surfaces.textPrimary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SharyRoomLoadingView(
    statusMessage: String
) {
    val surfaces = SharyThemeTokens.surfaces
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        SharyAnimatedLoadingLogo(
            lightColor = surfaces.logoSecondaryBar,
            darkColor = surfaces.logoPrimaryBar.copy(alpha = 0.45f),
            modifier = Modifier.size(112.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = statusMessage,
            color = surfaces.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

// Экран до создания/входа в комнату (без механики выбора мест/вместимости)
@Composable
private fun SharyRoomLobbyView(
    connectionManager: RoomController
) {
    val surfaces = SharyThemeTokens.surfaces
    val focusManager = LocalFocusManager.current

    var isCreateExpanded by rememberSaveable { mutableStateOf(false) }
    var createPassword by rememberSaveable { mutableStateOf("") }
    var createAdminOnly by rememberSaveable { mutableStateOf(true) }

    var joinCode by rememberSaveable { mutableStateOf("") }
    var joinPassword by rememberSaveable { mutableStateOf("") }
    var showPasswordsPlain by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // ВЕРХНИЙ БЛОК: СОЗДАНИЕ КОМНАТЫ
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Совместное прослушивание",
                color = surfaces.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            AnimatedVisibility(
                visible = isCreateExpanded,
                enter = fadeIn(tween(160)) + expandVertically(tween(180)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(150))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(surfaces.songRowFill)
                        .border(1.dp, surfaces.songRowBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Пароль комнаты (необязательно):",
                        color = surfaces.textSecondary,
                        fontSize = 12.sp
                    )

                    MonochromeTextField(
                        value = createPassword,
                        onValueChange = { createPassword = it.take(32) },
                        placeholder = "Пароль комнаты",
                        isPassword = !showPasswordsPlain,
                        imeAction = ImeAction.Done,
                        onImeAction = { focusManager.clearFocus() }
                    )

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
                            filled = createAdminOnly,
                            modifier = Modifier.weight(1f),
                            onClick = { createAdminOnly = true }
                        )
                        MonochromeActionPill(
                            text = "Права у всех",
                            filled = !createAdminOnly,
                            modifier = Modifier.weight(1f),
                            onClick = { createAdminOnly = false }
                        )
                    }
                }
            }

            val createInteraction = remember { MutableInteractionSource() }
            val isCreatePressed by createInteraction.collectIsPressedAsState()
            val createScale = rememberSharyInteractiveScaleState(isPressed = isCreatePressed, pressedScale = 1.04f)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .graphicsLayer {
                        val s = createScale.value
                        scaleX = s
                        scaleY = s
                    }
                    .clip(RoundedCornerShape(14.dp))
                    .background(surfaces.playerAccent)
                    .border(1.dp, surfaces.textPrimary.copy(alpha = 0.24f), RoundedCornerShape(14.dp))
                    .clickable(
                        interactionSource = createInteraction,
                        indication = null
                    ) {
                        if (!isCreateExpanded) {
                            isCreateExpanded = true
                        } else {
                            focusManager.clearFocus()
                            connectionManager.createRoom(
                                maxCapacity = RoomController.MAX_CAPACITY,
                                password = createPassword,
                                permissionMode = if (createAdminOnly) {
                                    RoomPermissionMode.ADMIN_ONLY
                                } else {
                                    RoomPermissionMode.OPEN
                                }
                            )
                        }
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SharyPlusIcon(
                    tint = surfaces.onAccent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Создать комнату",
                    color = surfaces.onAccent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(surfaces.songRowBorder)
            )
            Text(
                text = "или войти по коду",
                color = surfaces.textSecondary,
                fontSize = 12.sp
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(surfaces.songRowBorder)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // НИЖНИЙ БЛОК: ВХОД В КОМНАТУ ПО 6-ЗНАЧНОМУ КОДУ
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MonochromeTextField(
                value = joinCode,
                onValueChange = { raw ->
                    joinCode = raw
                        .uppercase(Locale.ROOT)
                        .filter { it in FirebaseConfig.ROOM_CHARACTERS }
                        .take(FirebaseConfig.ROOM_CODE_LENGTH)
                },
                placeholder = "6-значный код комнаты (например AB3K9P)",
                isPassword = false,
                capitalize = true,
                imeAction = ImeAction.Next,
                onImeAction = {}
            )

            MonochromeTextField(
                value = joinPassword,
                onValueChange = { joinPassword = it.take(32) },
                placeholder = "Пароль комнаты (если установлен)",
                isPassword = !showPasswordsPlain,
                imeAction = ImeAction.Go,
                onImeAction = {
                    focusManager.clearFocus()
                    if (joinCode.length == FirebaseConfig.ROOM_CODE_LENGTH) {
                        connectionManager.joinRoom(joinCode, joinPassword)
                    }
                }
            )

            Text(
                text = if (showPasswordsPlain) "Скрыть символы пароля" else "Показать символы пароля",
                color = surfaces.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { showPasswordsPlain = !showPasswordsPlain }
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )

            val cleanedStatus = sanitizeRoomStatusMessage(connectionManager.statusMessage)
            if (
                cleanedStatus.isNotBlank() &&
                cleanedStatus != "Создайте комнату или войдите по коду."
            ) {
                Text(
                    text = cleanedStatus,
                    color = surfaces.textSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            val canJoin = joinCode.length == FirebaseConfig.ROOM_CODE_LENGTH && !connectionManager.isBusy
            val joinInteraction = remember { MutableInteractionSource() }
            val isJoinPressed by joinInteraction.collectIsPressedAsState()
            val joinScale = rememberSharyInteractiveScaleState(isPressed = isJoinPressed, pressedScale = 1.05f)

            Row(
                modifier = Modifier
                    .height(48.dp)
                    .graphicsLayer {
                        val s = joinScale.value
                        scaleX = s
                        scaleY = s
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (canJoin) surfaces.playerAccent
                        else surfaces.songRowFill
                    )
                    .border(
                        width = 1.dp,
                        color = if (canJoin) surfaces.textPrimary.copy(alpha = 0.28f) else surfaces.songRowBorder,
                        shape = RoundedCornerShape(24.dp)
                    )
                    .clickable(
                        interactionSource = joinInteraction,
                        indication = null,
                        enabled = canJoin
                    ) {
                        focusManager.clearFocus()
                        connectionManager.joinRoom(joinCode, joinPassword)
                    }
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                val contentTint = if (canJoin) surfaces.onAccent else surfaces.textSecondary
                SharyJoinRoomPersonPlusIcon(
                    tint = contentTint,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Войти в комнату",
                    color = contentTint,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun MonochromeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean,
    capitalize: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {}
) {
    val surfaces = SharyThemeTokens.surfaces
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(surfaces.fieldFill)
            .border(1.dp, surfaces.fieldBorder, shape)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                color = surfaces.textSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation = if (isPassword) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            textStyle = TextStyle(
                color = surfaces.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            ),
            cursorBrush = SolidColor(surfaces.textPrimary),
            keyboardOptions = KeyboardOptions(
                capitalization = if (capitalize) {
                    KeyboardCapitalization.Characters
                } else {
                    KeyboardCapitalization.None
                },
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(
                onDone = { onImeAction() },
                onGo = { onImeAction() },
                onNext = { onImeAction() }
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// Активный экран Комнаты:
// - слева: увеличенная половина виниловой пластинки (с чёрным кругом-отверстием внутри, статичным логотипом без фото, угольными волнами и дуговым ползунком);
// - справа снизу: кнопки "+" (добавление новых песен) и "♡" (избранное);
// - справа над ними: прямой вертикальный слайдер мини-пластинок (как в воспроизведении, только с увеличением без смещения/подъёма),
//   плавно затухающий сверху перед названием/уголком чата и снизу перед "+" и "♡".
@Composable
private fun SharyActiveRoomPlayerView(
    connectionManager: RoomController,
    extendedRoom: RoomExtendedController,
    allLoadedSongs: List<Song>,
    userLibrarySongs: List<Song>,
    currentSongId: String?,
    favoriteSongIds: Set<String>,
    isPlaying: Boolean,
    positionMsFlow: StateFlow<Long>,
    durationMsFlow: StateFlow<Long>,
    onPlayRoomSong: (Song) -> Unit,
    onDownloadAndPlayRoomTrack: (RoomTrack) -> Unit,
    onShareSongToRoom: (Song) -> Unit,
    onAddNewSongsToRoomClick: () -> Unit,
    onSeekRoomSong: (Song, Long) -> Unit,
    onSaveRoomTrackToLibrary: (RoomTrack?, Song?) -> Unit,
    onToggleFavoriteSong: (String) -> Unit,
    onOpenChatClick: () -> Unit
) {
    val surfaces = SharyThemeTokens.surfaces
    val coroutineScope = rememberCoroutineScope()

    val metadataMap = rememberSongsMetadataMap(allLoadedSongs)

    val canShare = connectionManager.canShareMusic()
    val roomTracksSnapshot = connectionManager.roomTracks.toList()

    val plateItems = remember(roomTracksSnapshot, userLibrarySongs, allLoadedSongs, canShare) {
        val list = ArrayList<RoomBrowsePlate>()
        val usedNormTitles = HashSet<String>()
        val usedIds = HashSet<String>()

        for (rt in roomTracksSnapshot) {
            val matchedLocal = allLoadedSongs.firstOrNull { s ->
                s.id == rt.id ||
                    SongDuplicateGuard.normalizeSongTitle(s.title) ==
                    SongDuplicateGuard.normalizeSongTitle(rt.title)
            }
            list.add(
                RoomBrowsePlate(
                    id = rt.id,
                    title = rt.title,
                    roomTrack = rt,
                    localSong = matchedLocal
                )
            )
            usedIds.add(rt.id)
            usedNormTitles.add(SongDuplicateGuard.normalizeSongTitle(rt.title))
        }

        if (canShare) {
            for (local in userLibrarySongs) {
                val norm = SongDuplicateGuard.normalizeSongTitle(local.title)
                if (local.id !in usedIds && norm !in usedNormTitles) {
                    list.add(
                        RoomBrowsePlate(
                            id = local.id,
                            title = local.title,
                            roomTrack = null,
                            localSong = local
                        )
                    )
                    usedIds.add(local.id)
                    usedNormTitles.add(norm)
                }
            }
        }

        list
    }

    var pendingAutoPlaySong by remember { mutableStateOf<Song?>(null) }

    LaunchedEffect(roomTracksSnapshot.size, connectionManager.isBusy, pendingAutoPlaySong?.id) {
        val target = pendingAutoPlaySong ?: return@LaunchedEffect
        val uploaded = roomTracksSnapshot.any {
            it.id == target.id ||
                SongDuplicateGuard.normalizeSongTitle(it.title) ==
                SongDuplicateGuard.normalizeSongTitle(target.title)
        }
        if (uploaded && !connectionManager.isBusy) {
            pendingAutoPlaySong = null
            onPlayRoomSong(target)
        }
    }

    var browsedPlateId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(currentSongId) {
        if (currentSongId != null && plateItems.any { it.id == currentSongId || it.localSong?.id == currentSongId }) {
            browsedPlateId = currentSongId
        }
    }

    val activeSongInPlayer = remember(allLoadedSongs, currentSongId) {
        allLoadedSongs.firstOrNull { it.id == currentSongId }
    }

    val selectedPlateIndex = remember(plateItems, browsedPlateId, currentSongId) {
        if (plateItems.isEmpty()) {
            0
        } else {
            val byBrowse = plateItems.indexOfFirst {
                it.id == browsedPlateId || it.localSong?.id == browsedPlateId
            }
            if (byBrowse >= 0) {
                byBrowse
            } else {
                val byCurrent = plateItems.indexOfFirst {
                    it.id == currentSongId || it.localSong?.id == currentSongId
                }
                byCurrent.coerceAtLeast(0)
            }
        }
    }

    // В шапке отображаем выбранную мини-пластинку или играющий трек
    val displayedPlate = plateItems.getOrNull(selectedPlateIndex)
    val headerSong: Song? = displayedPlate?.localSong ?: activeSongInPlayer
    val headerRoomTrack: RoomTrack? = remember(roomTracksSnapshot, headerSong?.id, displayedPlate) {
        displayedPlate?.roomTrack ?: if (headerSong != null) {
            roomTracksSnapshot.firstOrNull {
                it.id == headerSong.id ||
                    SongDuplicateGuard.normalizeSongTitle(it.title) ==
                    SongDuplicateGuard.normalizeSongTitle(headerSong.title)
            }
        } else {
            null
        }
    }

    val headerTitle = headerSong?.title
        ?: headerRoomTrack?.title
        ?: displayedPlate?.title
        ?: "Добавьте песню кнопкой «+»"

    val headerMeta = headerSong?.let { metadataMap[it.id] }
    val fallbackDurMs = headerMeta?.durationMs ?: headerSong?.durationMs ?: 0L
    val isHeaderSongCurrent = headerSong != null && headerSong.id == currentSongId

    val livePosMs by positionMsFlow.collectAsState()
    val liveDurMs by durationMsFlow.collectAsState()
    val totalDurMs = if (isHeaderSongCurrent && liveDurMs > 0L) {
        liveDurMs
    } else {
        fallbackDurMs.coerceAtLeast(0L)
    }

    var scratchPreviewMs by remember(headerSong?.id, headerTitle) { mutableStateOf<Long?>(null) }
    val vinylRotationDeg = remember { mutableFloatStateOf(0f) }

    val shownPosMs = when {
        scratchPreviewMs != null -> scratchPreviewMs!!.coerceIn(0L, max(totalDurMs, scratchPreviewMs!!))
        isHeaderSongCurrent -> livePosMs.coerceIn(0L, max(totalDurMs, livePosMs))
        else -> 0L
    }
    val progressFraction = if (totalDurMs > 0L) {
        (shownPosMs.toFloat() / totalDurMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val targetTrackId = headerRoomTrack?.id ?: headerSong?.id.orEmpty()
    val isAlreadyInUserLibrary = remember(targetTrackId, headerTitle, userLibrarySongs) {
        targetTrackId.isNotEmpty() && SongDuplicateGuard.isTrackAlreadyInLibrary(
            trackId = targetTrackId,
            trackTitle = headerTitle,
            librarySongs = userLibrarySongs
        )
    }
    val canShowDownloadIcon = headerRoomTrack != null || headerSong != null

    val pagerState = rememberPagerState(
        initialPage = selectedPlateIndex,
        pageCount = { max(plateItems.size, 1) }
    )
    var programmaticScrollCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(selectedPlateIndex, plateItems.size) {
        if (plateItems.isNotEmpty() && selectedPlateIndex in plateItems.indices && selectedPlateIndex != pagerState.currentPage) {
            programmaticScrollCount += 1
            try {
                pagerState.animateScrollToPage(selectedPlateIndex)
            } finally {
                programmaticScrollCount = (programmaticScrollCount - 1).coerceAtLeast(0)
            }
        }
    }

    LaunchedEffect(pagerState, plateItems) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (programmaticScrollCount == 0 && page in plateItems.indices) {
                browsedPlateId = plateItems[page].id
            }
        }
    }

    val rightSliderColumnWidth = 112.dp

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. ЛЕВАЯ И ВЕРХНЯЯ ЧАСТЬ: ШАПКА С НАЗВАНИЕМ И ТАЙМЛАЙНАМИ + УВЕЛИЧЕННАЯ ПОЛУПЛАСТИНКА
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, top = 12.dp, end = 66.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0.0f to Color.Black,
                                    0.74f to Color.Black,
                                    1.0f to Color.Transparent
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = headerTitle,
                        color = surfaces.textPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatSongDuration(shownPosMs),
                        color = surfaces.textPrimary.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "|",
                        color = surfaces.textSecondary.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                    Text(
                        text = formatSongDuration(totalDurMs),
                        color = surfaces.textSecondary,
                        fontSize = 13.sp
                    )

                    if (canShowDownloadIcon) {
                        val dlDesc = if (isAlreadyInUserLibrary) {
                            stringResource(R.string.room_song_already_saved)
                        } else {
                            stringResource(R.string.action_download_room_song)
                        }
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .semantics {
                                    contentDescription = dlDesc
                                    role = Role.Button
                                }
                                .clip(CircleShape)
                                .clickable {
                                    if (isAlreadyInUserLibrary) {
                                        extendedRoom.toastNotice = "Эта песня уже есть в вашей библиотеке"
                                    } else {
                                        onSaveRoomTrackToLibrary(headerRoomTrack, headerSong)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            SharyDownloadTrackIcon(
                                isAlreadySaved = isAlreadyInUserLibrary,
                                tint = if (isAlreadyInUserLibrary) {
                                    surfaces.textSecondary.copy(alpha = 0.7f)
                                } else {
                                    surfaces.textPrimary
                                },
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }

                if (connectionManager.isBusy && connectionManager.progressText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = connectionManager.progressText,
                        color = surfaces.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // ОСНОВНАЯ СЦЕНА ПОЛУПЛАСТИНКИ: минимальный зазор сверху до таймлайнов и увеличенный диаметр
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds()
            ) {
                val density = LocalDensity.current
                val stageW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val stageH = constraints.maxHeight.toFloat().coerceAtLeast(1f)

                val rightColPx = with(density) { rightSliderColumnWidth.toPx() }
                val arcGapPx = with(density) { 9.dp.toPx() }
                val edgeMarginPx = with(density) { 8.dp.toPx() }

                // Увеличенный радиус пластинки: занимает почти всю высоту от верхних таймлайнов до низа
                // и аккуратно стыкуется слева от вертикального слайдера мини-пластинок
                val maxRadiusByHeight = (stageH * 0.48f) - arcGapPx
                val maxRadiusByWidth = ((stageW - rightColPx - arcGapPx - edgeMarginPx) / 1.16f)
                val discRadiusPx = min(maxRadiusByHeight, maxRadiusByWidth).coerceAtLeast(60f)

                val vinylCx = discRadiusPx * 0.16f
                val vinylCy = stageH * 0.49f
                val arcRadiusPx = discRadiusPx + arcGapPx

                RoomHalfVinylWithArcScrubber(
                    songKey = headerSong?.id ?: headerTitle,
                    coverBitmap = headerMeta?.coverBitmap,
                    isSpinning = isHeaderSongCurrent && isPlaying,
                    canControlSeek = connectionManager.canControlPlayback() && headerSong != null,
                    totalDurationMs = totalDurMs,
                    currentProgressFraction = progressFraction,
                    rotationDeg = vinylRotationDeg,
                    vinylCx = vinylCx,
                    vinylCy = vinylCy,
                    discRadiusPx = discRadiusPx,
                    arcRadiusPx = arcRadiusPx,
                    onScratchPreviewMs = { preview -> scratchPreviewMs = preview },
                    onCommitSeekMs = { targetMs ->
                        val song = headerSong
                        if (song != null && connectionManager.canControlPlayback()) {
                            onSeekRoomSong(song, targetMs)
                        } else if (!connectionManager.canControlPlayback()) {
                            extendedRoom.toastNotice = "Перематывать музыку могут только админы"
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 2. ПРАВАЯ КОЛОНКА: ВЕРТИКАЛЬНЫЙ СЛАЙДЕР ПЕСЕН НАД ЗНАЧКАМИ "+" И "♡"
        // Слайдер пропадает сверху перед названием/уголком чата и снизу перед значками "+" и "♡"
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .width(rightSliderColumnWidth)
                .fillMaxHeight()
                .padding(top = 52.dp, bottom = 10.dp, end = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (plateItems.isNotEmpty()) {
                    val density = LocalDensity.current
                    val plateSize = SharyDimens.RoomMiniPlateSize
                    val plateSizePx = with(density) { plateSize.toPx() }
                    val sliderHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                    val verticalPad = with(density) {
                        ((sliderHeightPx - plateSizePx) / 2f).coerceAtLeast(0f).toDp()
                    }

                    VerticalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(vertical = verticalPad),
                        pageSize = PageSize.Fixed(plateSize),
                        pageSpacing = 12.dp,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                            .drawWithContent {
                                drawContent()
                                // Плавное исчезновение песен сверху (перед названием и уголком чата)
                                // и снизу (перед кнопками "+" и "♡")
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        0.0f to Color.Transparent,
                                        0.16f to Color.Black,
                                        0.84f to Color.Black,
                                        1.0f to Color.Transparent
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                    ) { page ->
                        val item = plateItems[page]
                        val itemMeta = item.localSong?.let { metadataMap[it.id] }
                        val isCentered = pagerState.currentPage == page
                        val isActiveTrack = (item.localSong != null && item.localSong.id == currentSongId) ||
                            item.id == currentSongId

                        MiniSongPlateCard(
                            title = item.title,
                            coverBitmap = itemMeta?.coverBitmap,
                            isCentered = isCentered,
                            isPlayingPlate = isActiveTrack && isPlaying,
                            onClick = {
                                browsedPlateId = item.id
                                if (pagerState.currentPage != page) {
                                    coroutineScope.launch {
                                        programmaticScrollCount += 1
                                        try {
                                            pagerState.animateScrollToPage(page)
                                        } finally {
                                            programmaticScrollCount = (programmaticScrollCount - 1).coerceAtLeast(0)
                                        }
                                    }
                                }

                                if (!connectionManager.canControlPlayback() && !connectionManager.canShareMusic()) {
                                    extendedRoom.toastNotice =
                                        "Отправлять и включать музыку могут только админы"
                                    return@MiniSongPlateCard
                                }

                                if (connectionManager.isBusy) {
                                    extendedRoom.toastNotice = "Дождитесь завершения текущей передачи..."
                                    return@MiniSongPlateCard
                                }

                                if (item.roomTrack != null) {
                                    val localMatch = item.localSong ?: allLoadedSongs.firstOrNull {
                                        it.id == item.roomTrack.id ||
                                            SongDuplicateGuard.normalizeSongTitle(it.title) ==
                                            SongDuplicateGuard.normalizeSongTitle(item.roomTrack.title)
                                    }
                                    if (localMatch != null) {
                                        onPlayRoomSong(localMatch)
                                    } else {
                                        onDownloadAndPlayRoomTrack(item.roomTrack)
                                    }
                                } else if (item.localSong != null && connectionManager.canShareMusic()) {
                                    pendingAutoPlaySong = item.localSong
                                    onShareSongToRoom(item.localSong)
                                }
                            },
                            modifier = Modifier
                                .size(plateSize)
                                .graphicsLayer {
                                    // Строго прямой вертикальный слайдер без бокового сдвига и без подъёма:
                                    // только плавное увеличение отцентрованной пластинки!
                                    val dist = abs(
                                        (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                                    ).coerceIn(0f, 1.5f)
                                    val scale = 1.0f - 0.14f * dist
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = 1.0f - 0.18f * dist.coerceAtMost(1f)
                                }
                        )
                    }
                } else {
                    SharyLogoMark(
                        primaryColor = surfaces.placeholderLogoPrimary.copy(alpha = 0.28f),
                        secondaryColor = surfaces.placeholderLogoSecondary.copy(alpha = 0.42f),
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // СНИЗУ СПРАВА В ОБЛАСТИ: ЗНАК ПЛЮС (+) И СЕРДЦЕ (♡)
            val favId = headerSong?.id ?: headerRoomTrack?.id
            val isFav = favId != null && favId in favoriteSongIds
            val heartTint by animateColorAsState(
                targetValue = if (isFav) surfaces.heartActive else surfaces.navIconInactive,
                animationSpec = tween(120),
                label = "roomBottomHeartTint"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Кнопка "+" добавления новых песен
                val addDesc = stringResource(R.string.action_add_songs)
                val addInteraction = remember { MutableInteractionSource() }
                val isAddPressed by addInteraction.collectIsPressedAsState()
                val addScale = rememberSharyInteractiveScaleState(isPressed = isAddPressed, pressedScale = 1.12f)

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .graphicsLayer {
                            val s = addScale.value
                            scaleX = s
                            scaleY = s
                        }
                        .semantics {
                            contentDescription = addDesc
                            role = Role.Button
                        }
                        .clip(CircleShape)
                        .background(surfaces.playerAccent)
                        .border(1.dp, surfaces.textPrimary.copy(alpha = 0.22f), CircleShape)
                        .clickable(
                            interactionSource = addInteraction,
                            indication = null,
                            onClick = onAddNewSongsToRoomClick
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    SharyPlusIcon(
                        tint = surfaces.onAccent,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Кнопка "♡" избранного
                val heartInteraction = remember { MutableInteractionSource() }
                val isHeartPressed by heartInteraction.collectIsPressedAsState()
                val heartScale = rememberSharyInteractiveScaleState(
                    isSelected = isFav,
                    isPressed = isHeartPressed,
                    pressedScale = 1.14f
                )

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .graphicsLayer {
                            val s = heartScale.value
                            scaleX = s
                            scaleY = s
                        }
                        .clip(CircleShape)
                        .background(surfaces.songRowFill)
                        .border(1.dp, surfaces.songRowBorder, CircleShape)
                        .clickable(
                            interactionSource = heartInteraction,
                            indication = null,
                            enabled = favId != null
                        ) {
                            if (favId != null) {
                                onToggleFavoriteSong(favId)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    SharyHeartIcon(
                        filled = isFav,
                        tint = heartTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 3. ПРАВЫЙ ВЕРХНИЙ УГОЛОК 64.dp С БЕЛЫМ ЗНАЧКОМ ЧАТА
        RoomChatCornerTriangleButton(
            unreadCount = extendedRoom.unreadChatCount,
            onClick = onOpenChatClick,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

@Composable
private fun RoomChatCornerTriangleButton(
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val surfaces = SharyThemeTokens.surfaces
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scaleState = rememberSharyInteractiveScaleState(
        isSelected = unreadCount > 0,
        isPressed = isPressed,
        pressedScale = 1.14f
    )
    val bgColorState = animateColorAsState(
        targetValue = if (isPressed) {
            surfaces.playerAccent.copy(alpha = 0.80f)
        } else {
            surfaces.playerAccent
        },
        animationSpec = tween(110),
        label = "roomChatCornerBg"
    )
    val chatDesc = stringResource(R.string.action_open_room_chat)

    Box(
        modifier = modifier
            .size(SharyDimens.PlayerCornerTriangleSize)
            .semantics {
                contentDescription = chatDesc
                role = Role.Button
            }
            .clip(TopRightCornerTriangleShape)
            .drawBehind {
                drawRect(color = bgColorState.value)
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.TopEnd
    ) {
        Box(
            modifier = Modifier
                .padding(top = 9.dp, end = 9.dp)
                .size(23.dp)
                .graphicsLayer {
                    val s = scaleState.value
                    scaleX = s
                    scaleY = s
                },
            contentAlignment = Alignment.Center
        ) {
            SharyChatBubbleIcon(
                tint = surfaces.onAccent,
                modifier = Modifier.size(20.dp)
            )

            if (unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(surfaces.onAccent)
                )
            }
        }
    }
}

// Половина большой пластинки слева + изогнутый дуговой таймлайн вокруг неё:
// полностью совпадает по виду с пластинкой из режима воспроизведения:
// - металлическая фаска обода + 6 дорожек с острыми угольными волнами;
// - статичный серый логотип SHARY, если у песни нет обложки;
// - чёрный круг-отверстие с тонкой серой окантовкой строго в центре пластинки!
@Composable
private fun RoomHalfVinylWithArcScrubber(
    songKey: String,
    coverBitmap: ImageBitmap?,
    isSpinning: Boolean,
    canControlSeek: Boolean,
    totalDurationMs: Long,
    currentProgressFraction: Float,
    rotationDeg: androidx.compose.runtime.MutableFloatState,
    vinylCx: Float,
    vinylCy: Float,
    discRadiusPx: Float,
    arcRadiusPx: Float,
    onScratchPreviewMs: (Long?) -> Unit,
    onCommitSeekMs: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val surfaces = SharyThemeTokens.surfaces
    val density = LocalDensity.current
    val arcHitSlopPx = with(density) { 24.dp.toPx() }
    val minDeadZonePx = with(density) { 10.dp.toPx() }

    val arcStartDeg = -66f
    val arcSweepTotalDeg = 132f

    var isUserScrubbing by remember { mutableStateOf(false) }

    val latestCanSeek by rememberUpdatedState(canControlSeek)
    val latestTotalDur by rememberUpdatedState(totalDurationMs)
    val latestProgress by rememberUpdatedState(currentProgressFraction)
    val latestOnPreview by rememberUpdatedState(onScratchPreviewMs)
    val latestOnCommit by rememberUpdatedState(onCommitSeekMs)

    // При открытии трека в паузе ориентируем сектор острых волн к текущему положению ползунка на дуге
    LaunchedEffect(songKey) {
        if (!isSpinning) {
            val targetAngle = arcStartDeg + arcSweepTotalDeg * latestProgress.coerceIn(0f, 1f)
            rotationDeg.floatValue = (targetAngle + 360f) % 360f
        }
    }

    // Авто-вращение пластинки во время воспроизведения
    LaunchedEffect(isSpinning, isUserScrubbing) {
        if (!isSpinning || isUserScrubbing) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dt = ((frameNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    rotationDeg.floatValue = (rotationDeg.floatValue + dt * 38f) % 360f
                }
                lastNanos = frameNanos
            }
        }
    }

    val groovePaths = remember(discRadiusPx) {
        Array(6) { idx ->
            val baseR = discRadiusPx * (0.42f + idx * 0.088f)
            buildSharpSpikyVinylGroove(
                cx = 0f,
                cy = 0f,
                ringIndex = idx,
                baseR = baseR,
                waveAmp = discRadiusPx * 0.015f,
                maxSpikeAmp = discRadiusPx * 0.036f,
                zigzagCenterRad = 0f,
                zigzagHalfSpanRad = 0.48f,
                phaseOffset = idx * 0.35f
            )
        }
    }

    val rimBrush = remember {
        Brush.sweepGradient(
            0.0f to SharyMilk.copy(alpha = 0.06f),
            0.18f to SharyMilk.copy(alpha = 0.24f),
            0.36f to SharyMilk.copy(alpha = 0.05f),
            0.68f to SharyMilk.copy(alpha = 0.20f),
            1.0f to SharyMilk.copy(alpha = 0.06f),
            center = Offset.Zero
        )
    }

    Box(
        modifier = modifier.pointerInput(songKey, vinylCx, vinylCy, discRadiusPx, arcRadiusPx) {
            val twoPi = (2.0 * PI).toFloat()
            val radToDeg = (180.0 / PI).toFloat()

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val dx0 = down.position.x - vinylCx
                val dy0 = down.position.y - vinylCy
                val dist0 = hypot(dx0, dy0)

                val isOnArc = abs(dist0 - arcRadiusPx) <= arcHitSlopPx && dx0 > -discRadiusPx * 0.1f
                val isOnDisc = dist0 < discRadiusPx - arcHitSlopPx * 0.35f

                if (!isOnArc && !isOnDisc) return@awaitEachGesture

                var prevAngleRad = atan2(dy0, dx0)
                val dur = latestTotalDur
                val msPerTurn = if (dur > 0L) (dur * 0.12f).coerceIn(14_000f, 30_000f) else 20_000f
                var scrubbedMs = (latestProgress * dur).toLong().coerceIn(0L, max(dur, 0L))
                var didMove = false
                var lastLiveCommit = 0L

                if (isOnArc && dur > 0L && latestCanSeek) {
                    val deg0 = (prevAngleRad * radToDeg).coerceIn(arcStartDeg, arcStartDeg + arcSweepTotalDeg)
                    val frac0 = ((deg0 - arcStartDeg) / arcSweepTotalDeg).coerceIn(0f, 1f)
                    rotationDeg.floatValue = (deg0 + 360f) % 360f
                    scrubbedMs = (frac0 * dur).toLong()
                    latestOnPreview(scrubbedMs)
                    didMove = true
                }

                isUserScrubbing = true
                try {
                    drag(down.id) { change ->
                        val dx = change.position.x - vinylCx
                        val dy = change.position.y - vinylCy
                        val dist = hypot(dx, dy)

                        if (isOnArc) {
                            change.consume()
                            didMove = true
                            val angleDeg = (atan2(dy, dx) * radToDeg)
                                .coerceIn(arcStartDeg, arcStartDeg + arcSweepTotalDeg)
                            val newFrac = ((angleDeg - arcStartDeg) / arcSweepTotalDeg).coerceIn(0f, 1f)

                            // Вращаем пластинку и её сектор острых волн синхронно за ползунком дуги
                            rotationDeg.floatValue = (angleDeg + 360f) % 360f

                            if (dur > 0L && latestCanSeek) {
                                scrubbedMs = (newFrac * dur).toLong().coerceIn(0L, dur)
                                latestOnPreview(scrubbedMs)
                                val now = SystemClock.uptimeMillis()
                                if (now - lastLiveCommit >= 90L) {
                                    lastLiveCommit = now
                                    latestOnCommit(scrubbedMs)
                                }
                            }
                        } else if (isOnDisc && dist >= minDeadZonePx) {
                            val curAngle = atan2(dy, dx)
                            var dRad = curAngle - prevAngleRad
                            if (dRad > PI.toFloat()) dRad -= twoPi
                            if (dRad < -PI.toFloat()) dRad += twoPi
                            prevAngleRad = curAngle

                            if (abs(dRad) > 0.001f) {
                                change.consume()
                                didMove = true

                                val nextRot = (rotationDeg.floatValue + dRad * radToDeg) % 360f
                                rotationDeg.floatValue = if (nextRot < 0f) nextRot + 360f else nextRot

                                if (dur > 0L && latestCanSeek) {
                                    val deltaMs = ((dRad / twoPi) * msPerTurn).toLong()
                                    scrubbedMs = (scrubbedMs + deltaMs).coerceIn(0L, dur)
                                    latestOnPreview(scrubbedMs)
                                    val now = SystemClock.uptimeMillis()
                                    if (now - lastLiveCommit >= 90L) {
                                        lastLiveCommit = now
                                        latestOnCommit(scrubbedMs)
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    isUserScrubbing = false
                    if (didMove) {
                        if (latestCanSeek && dur > 0L) {
                            latestOnCommit(scrubbedMs)
                        } else if (!latestCanSeek) {
                            latestOnCommit(0L)
                        }
                    }
                    latestOnPreview(null)
                }
            }
        }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val rot = rotationDeg.floatValue
            val grooveStrokePx = 1.4.dp.toPx()
            val rimStrokePx = 1.6.dp.toPx()
            val arcStrokePx = 4.4.dp.toPx()
            val thumbRadiusPx = if (isUserScrubbing) 9.5.dp.toPx() else 8.0.dp.toPx()

            clipRect(0f, 0f, size.width, size.height) {
                // 1. Вращающаяся виниловая пластинка (точно как в SharySpinningVinylDisc)
                withTransform({
                    translate(left = vinylCx, top = vinylCy)
                    rotate(degrees = rot, pivot = Offset.Zero)
                }) {
                    drawCircle(
                        color = surfaces.vinylDiscSurface,
                        radius = discRadiusPx,
                        center = Offset.Zero
                    )
                    drawCircle(
                        brush = rimBrush,
                        radius = discRadiusPx - rimStrokePx,
                        center = Offset.Zero,
                        style = Stroke(width = rimStrokePx)
                    )

                    val grooveStyle = Stroke(
                        width = grooveStrokePx,
                        cap = StrokeCap.Butt,
                        join = StrokeJoin.Miter,
                        miter = 10f
                    )
                    for (gp in groovePaths) {
                        drawPath(
                            path = gp,
                            color = surfaces.vinylGrooveStroke,
                            style = grooveStyle
                        )
                    }

                    // Центральное свечение вокруг яблока пластинки (как в режиме воспроизведения)
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.0f to surfaces.playerAccent.copy(alpha = 0.80f),
                            0.55f to surfaces.playerCenterGlow.copy(alpha = 0.50f),
                            0.82f to surfaces.playerCenterGlow.copy(alpha = 0.18f),
                            1.0f to Color.Transparent,
                            center = Offset.Zero,
                            radius = discRadiusPx * 0.42f
                        ),
                        radius = discRadiusPx * 0.42f,
                        center = Offset.Zero
                    )
                }

                // 2. Дуговой таймлайн справа вокруг пластинки
                val arcRect = Rect(
                    left = vinylCx - arcRadiusPx,
                    top = vinylCy - arcRadiusPx,
                    right = vinylCx + arcRadiusPx,
                    bottom = vinylCy + arcRadiusPx
                )
                drawArc(
                    color = surfaces.playerTrackInactive,
                    startAngle = arcStartDeg,
                    sweepAngle = arcSweepTotalDeg,
                    useCenter = false,
                    topLeft = arcRect.topLeft,
                    size = arcRect.size,
                    style = Stroke(width = arcStrokePx, cap = StrokeCap.Round)
                )

                val activeSweep = arcSweepTotalDeg * currentProgressFraction.coerceIn(0f, 1f)
                if (activeSweep > 0.4f) {
                    drawArc(
                        color = surfaces.textPrimary.copy(alpha = 0.88f),
                        startAngle = arcStartDeg,
                        sweepAngle = activeSweep,
                        useCenter = false,
                        topLeft = arcRect.topLeft,
                        size = arcRect.size,
                        style = Stroke(width = arcStrokePx, cap = StrokeCap.Round)
                    )
                }

                val thumbAngleRad = ((arcStartDeg + activeSweep) * (PI / 180.0)).toFloat()
                val thumbCenter = Offset(
                    x = vinylCx + arcRadiusPx * cos(thumbAngleRad),
                    y = vinylCy + arcRadiusPx * sin(thumbAngleRad)
                )
                drawCircle(
                    color = surfaces.textPrimary.copy(alpha = 0.22f),
                    radius = thumbRadiusPx + 3.dp.toPx(),
                    center = thumbCenter
                )
                drawCircle(
                    color = surfaces.textPrimary,
                    radius = thumbRadiusPx,
                    center = thumbCenter
                )
            }
        }

        // Яблоко пластинки (0.33 от диаметра = 0.66 от радиуса * 2 -> радиус 0.33 * discRadiusPx)
        // + чёрный круг-отверстие с тонкой серой окантовкой строго в центре пластинки (как в режиме воспроизведения!)
        val labelRadiusPx = discRadiusPx * 0.33f
        val labelDiameterDp = with(density) { (labelRadiusPx * 2f).toDp() }
        val holeRadiusPx = discRadiusPx * 0.068f
        val holeDiameterDp = with(density) { (holeRadiusPx * 2f).toDp() }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (vinylCx - labelRadiusPx).roundToInt(),
                        y = (vinylCy - labelRadiusPx).roundToInt()
                    )
                }
                .size(labelDiameterDp)
                .graphicsLayer {
                    rotationZ = rotationDeg.floatValue
                }
                .clip(CircleShape)
                .border(1.dp, SharyMilk.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (coverBitmap != null) {
                Image(
                    bitmap = coverBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Статичный серый логотип SHARY на тёмном фоне яблока (точь-в-точь как в режиме воспроизведения!)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val r = size.minDimension / 2f
                            drawCircle(color = surfaces.vinylDiscSurface)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    0.0f to surfaces.playerAccent.copy(alpha = 0.72f),
                                    0.58f to surfaces.playerCenterGlow.copy(alpha = 0.44f),
                                    1.0f to Color.Transparent,
                                    center = center,
                                    radius = r
                                ),
                                radius = r
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    SharyLogoMark(
                        primaryColor = SharyMilk.copy(alpha = 0.46f),
                        secondaryColor = SharyMilk.copy(alpha = 0.70f),
                        modifier = Modifier.fillMaxSize(0.64f)
                    )
                }
            }

            // Небольшой чёрный круг-отверстие с серой обводкой строго по центру пластинки
            Box(
                modifier = Modifier
                    .size(holeDiameterDp)
                    .clip(CircleShape)
                    .background(surfaces.vinylDiscSurface)
                    .border(
                        width = 1.dp,
                        color = SharyMilk.copy(alpha = 0.24f),
                        shape = CircleShape
                    )
            )
        }
    }
}
