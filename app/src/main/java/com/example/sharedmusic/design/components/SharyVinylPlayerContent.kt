package com.example.sharedmusic.design.components

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sharedmusic.R
import com.example.sharedmusic.design.theme.SharyBlack
import com.example.sharedmusic.design.theme.SharyDimens
import com.example.sharedmusic.design.theme.SharyMilk
import com.example.sharedmusic.design.theme.SharyThemeTokens
import com.example.sharedmusic.logic.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

// Экран винилового проигрывателя внутри 2-й серой области:
// - острый зигзагообразный участок дорожек на пластинке;
// - вращение пластинки пальцем для перемотки песни вперёд/назад;
// - сердечко выровнено правее (над последними цифрами времени трека);
// - боковые кнопки Shuffle/Repeat слегка опущены дугой;
// - мини-пластинки снизу плавно огибают дугой опущенный центральный круг "+".
@Composable
fun SharyVinylPlayerContent(
    songs: List<Song>,
    selectedSongId: String?,
    currentSongId: String?,
    favoriteSongIds: Set<String>,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    isRepeatEnabled: Boolean,
    positionMsFlow: StateFlow<Long> = remember { MutableStateFlow(0L) },
    durationMsFlow: StateFlow<Long> = remember { MutableStateFlow(0L) },
    onCollapseToList: () -> Unit,
    onSelectSongOnly: (Song) -> Unit,
    onPlaySpecificSong: (Song) -> Unit,
    onPlayPauseSelected: (Song) -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onSeekSelectedSong: (Song, Long) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    if (songs.isEmpty()) return

    val surfaces = SharyThemeTokens.surfaces
    val density = LocalDensity.current
    val metadataMap = rememberSongsMetadataMap(songs)

    val selectedIndex = remember(songs, selectedSongId, currentSongId) {
        val bySelected = songs.indexOfFirst { it.id == selectedSongId }
        if (bySelected >= 0) {
            bySelected
        } else {
            songs.indexOfFirst { it.id == currentSongId }.coerceAtLeast(0)
        }
    }
    val displayedSong = songs[selectedIndex]
    val displayedMeta = metadataMap[displayedSong.id] ?: rememberSongExtraMetadata(displayedSong).value
    val isSelectedSongCurrent = displayedSong.id == currentSongId
    val isSelectedPlaying = isSelectedSongCurrent && isPlaying
    val isFavorite = displayedSong.id in favoriteSongIds
    val fallbackDurationMs = displayedMeta.durationMs.coerceAtLeast(displayedSong.durationMs)

    // Живое превью позиции в мс во время ручного вращения виниловой пластинки пальцем
    var vinylScratchPreviewMs by remember(displayedSong.id) { mutableStateOf<Long?>(null) }
    val vinylRotationDeg = remember { mutableFloatStateOf(0f) }

    val pagerState = rememberPagerState(
        initialPage = selectedIndex,
        pageCount = { songs.size }
    )
    val coroutineScope = rememberCoroutineScope()
    var programmaticScrollCount by remember { mutableIntStateOf(0) }
    val latestSelectedId by rememberUpdatedState(displayedSong.id)
    val latestOnSelectOnly by rememberUpdatedState(onSelectSongOnly)

    LaunchedEffect(selectedIndex, songs.size) {
        if (selectedIndex in songs.indices && selectedIndex != pagerState.currentPage) {
            programmaticScrollCount += 1
            try {
                pagerState.animateScrollToPage(selectedIndex)
            } finally {
                programmaticScrollCount = (programmaticScrollCount - 1).coerceAtLeast(0)
            }
        }
    }

    LaunchedEffect(pagerState, songs) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (programmaticScrollCount == 0 && page in songs.indices) {
                val settled = songs[page]
                if (settled.id != latestSelectedId) {
                    latestOnSelectOnly(settled)
                }
            }
        }
    }

    val archLiftPx = with(density) { SharyDimens.PlayerMiniPlateArchLift.toPx() }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Вращающаяся виниловая пластинка (вращается сама при игре + можно крутить пальцем для перемотки)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                SharySpinningVinylDisc(
                    songId = displayedSong.id,
                    coverBitmap = displayedMeta.coverBitmap,
                    songTitle = displayedSong.title,
                    isSpinning = isSelectedPlaying,
                    isSelectedSongCurrent = isSelectedSongCurrent,
                    fallbackDurationMs = fallbackDurationMs,
                    rotationDeg = vinylRotationDeg,
                    positionMsFlow = positionMsFlow,
                    durationMsFlow = durationMsFlow,
                    onScratchPreviewMs = { previewMs -> vinylScratchPreviewMs = previewMs },
                    onCommitSeekMs = { targetMs -> onSeekSelectedSong(displayedSong, targetMs) },
                    modifier = Modifier
                        .fillMaxHeight()
                        .sizeIn(
                            maxWidth = SharyDimens.PlayerVinylMaxSize,
                            maxHeight = SharyDimens.PlayerVinylMaxSize
                        )
                        .aspectRatio(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Название трека + сердечко чуть правее (строго на уровне последних цифр длительности, например «04»)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0.0f to Color.Black,
                                    0.76f to Color.Black,
                                    1.0f to Color.Transparent
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = displayedSong.title,
                        color = surfaces.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }

                val favLabel = if (isFavorite) {
                    stringResource(R.string.action_favorite_remove)
                } else {
                    stringResource(R.string.action_favorite_add)
                }
                val heartTint by animateColorAsState(
                    targetValue = if (isFavorite) surfaces.playerAccent else surfaces.navIconInactive,
                    animationSpec = tween(120),
                    label = "playerHeartTint"
                )
                PlayerSmallIconButton(
                    label = favLabel,
                    isSelected = isFavorite,
                    touchSize = 36.dp,
                    onClick = { onToggleFavorite(displayedSong) }
                ) {
                    SharyHeartIcon(
                        filled = isFavorite,
                        tint = heartTint,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 3. Таймлайн перемотки + текущая секунда слева и полная длительность справа
            PlayerTimelineSection(
                positionMsFlow = positionMsFlow,
                durationMsFlow = durationMsFlow,
                externalScratchMs = vinylScratchPreviewMs,
                isSelectedSongCurrent = isSelectedSongCurrent,
                fallbackDurationMs = fallbackDurationMs,
                onScrubRotateDegrees = { deltaDeg ->
                    val next = (vinylRotationDeg.floatValue + deltaDeg) % 360f
                    vinylRotationDeg.floatValue = if (next < 0f) next + 360f else next
                },
                onSeekToMs = { targetMs -> onSeekSelectedSong(displayedSong, targetMs) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 4. Кнопки управления (боковые Shuffle и Repeat опущены дугой ниже центральных кнопок как на фото)
            PlayerTransportControlsRow(
                isPlaying = isSelectedPlaying,
                isShuffleEnabled = isShuffleEnabled,
                isRepeatEnabled = isRepeatEnabled,
                onShuffleClick = onShuffleClick,
                onPreviousClick = onPreviousClick,
                onPlayPauseClick = { onPlayPauseSelected(displayedSong) },
                onNextClick = onNextClick,
                onRepeatClick = onRepeatClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(2.dp))

            // 5. Слайдер мини-пластинок, огибающий дугой центральный круг "+" (центральная выше, боковые ниже по краям круга)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SharyDimens.PlayerMiniPlateSize + SharyDimens.PlayerMiniPlateArchLift + 6.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                val plateSize = SharyDimens.PlayerMiniPlateSize
                val sidePadding = ((maxWidth - plateSize) / 2).coerceAtLeast(0.dp)

                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = sidePadding),
                    pageSize = PageSize.Fixed(plateSize),
                    pageSpacing = 14.dp,
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val plateSong = songs[page]
                    val plateMeta = metadataMap[plateSong.id]
                    val isCentered = pagerState.currentPage == page
                    val isCurrentlyPlayingPlate = plateSong.id == currentSongId && isPlaying

                    MiniSongPlateCard(
                        title = plateSong.title,
                        coverBitmap = plateMeta?.coverBitmap,
                        isCentered = isCentered,
                        isPlayingPlate = isCurrentlyPlayingPlate,
                        onClick = {
                            if (pagerState.currentPage != page || abs(pagerState.currentPageOffsetFraction) > 0.01f) {
                                coroutineScope.launch {
                                    programmaticScrollCount += 1
                                    try {
                                        pagerState.animateScrollToPage(page)
                                    } finally {
                                        programmaticScrollCount = (programmaticScrollCount - 1).coerceAtLeast(0)
                                    }
                                }
                            }
                            onPlaySpecificSong(plateSong)
                        },
                        modifier = Modifier
                            .size(plateSize)
                            .graphicsLayer {
                                val dist = abs(
                                    (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                                ).coerceIn(0f, 1f)
                                // Плавная дуга огибания круга "+": в центре пластинка поднимается над "+", а по бокам опускается вокруг него
                                val archFactor = cos(dist * (PI / 2.0)).toFloat()
                                translationY = -archLiftPx * archFactor
                                val scale = 1.0f - 0.11f * dist
                                scaleX = scale
                                scaleY = scale
                                alpha = 1.0f - 0.20f * dist
                            }
                    )
                }
            }
        }

        // Правый верхний уголок со стрелкой вниз
        PlayerTopRightCornerButton(
            onClick = onCollapseToList,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

internal object TopRightCornerTriangleShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
private fun PlayerTopRightCornerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val surfaces = SharyThemeTokens.surfaces
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()

    val bobCenterPx = with(density) { 2.0.dp.toPx() }
    val bobAmplitudePx = with(density) { 2.2.dp.toPx() }
    val dipOffsetPx = bobCenterPx + bobAmplitudePx
    val arrowOffsetY = remember { Animatable(0f) }
    var everPressed by remember { mutableStateOf(false) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            everPressed = true
            // В момент нажатия стрелка слегка и плавно опускается вниз
            arrowOffsetY.animateTo(
                targetValue = dipOffsetPx,
                animationSpec = tween(durationMillis = 95, easing = FastOutSlowInEasing)
            )
            // При зажиме стрелка плавно двигается вверх-вниз (без рывка из точки dipOffsetPx)
            var startNanos = 0L
            val omega = (2.0 * PI * 2.15).toFloat()
            while (true) {
                val targetY = withFrameNanos { frameNanos ->
                    if (startNanos == 0L) startNanos = frameNanos
                    val elapsedSec = (frameNanos - startNanos) / 1_000_000_000f
                    bobCenterPx + bobAmplitudePx * cos(omega * elapsedSec)
                }
                arrowOffsetY.snapTo(targetY)
            }
        } else if (everPressed) {
            if (arrowOffsetY.value < dipOffsetPx * 0.60f) {
                arrowOffsetY.animateTo(
                    targetValue = dipOffsetPx * 0.85f,
                    animationSpec = tween(durationMillis = 65, easing = FastOutSlowInEasing)
                )
            }
            arrowOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 130, easing = FastOutSlowInEasing)
            )
        }
    }

    val bgColorState = animateColorAsState(
        targetValue = if (isPressed) {
            surfaces.playerAccent.copy(alpha = 0.80f)
        } else {
            surfaces.playerAccent
        },
        animationSpec = tween(110),
        label = "cornerTriangleColor"
    )
    val collapseDesc = stringResource(R.string.action_collapse_player)

    Box(
        modifier = modifier
            .size(SharyDimens.PlayerCornerTriangleSize)
            .semantics {
                contentDescription = collapseDesc
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
                .padding(top = 10.dp, end = 10.dp)
                .size(22.dp)
                .graphicsLayer {
                    translationY = arrowOffsetY.value
                },
            contentAlignment = Alignment.Center
        ) {
            SharyChevronDownIcon(
                tint = surfaces.onAccent,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// Вращающаяся виниловая пластинка с острым участком зигзагов и ручным вращением пальцем для перемотки трека
@Composable
private fun SharySpinningVinylDisc(
    songId: String,
    coverBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    songTitle: String,
    isSpinning: Boolean,
    isSelectedSongCurrent: Boolean,
    fallbackDurationMs: Long,
    rotationDeg: androidx.compose.runtime.MutableFloatState,
    positionMsFlow: StateFlow<Long>,
    durationMsFlow: StateFlow<Long>,
    onScratchPreviewMs: (Long?) -> Unit,
    onCommitSeekMs: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val surfaces = SharyThemeTokens.surfaces
    val density = LocalDensity.current
    val minDeadZonePx = with(density) { 10.dp.toPx() }

    var isFingerScratching by remember { mutableStateOf(false) }

    val latestIsCurrent by rememberUpdatedState(isSelectedSongCurrent)
    val latestFallbackDur by rememberUpdatedState(fallbackDurationMs)
    val latestOnPreview by rememberUpdatedState(onScratchPreviewMs)
    val latestOnCommitSeek by rememberUpdatedState(onCommitSeekMs)

    // Автоматическое вращение пластинки (останавливается, когда пользователь крутит пластинку пальцем или на паузе)
    LaunchedEffect(isSpinning, isFingerScratching) {
        if (!isSpinning || isFingerScratching) return@LaunchedEffect
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

    val discColor = surfaces.vinylDiscSurface
    val grooveColor = surfaces.vinylGrooveStroke
    val glowColor = surfaces.playerCenterGlow
    val accentColor = surfaces.playerAccent

    Box(
        modifier = modifier
            .pointerInput(songId) {
                val twoPi = (2.0 * PI).toFloat()
                val radToDeg = (180.0 / PI).toFloat()

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    var prevAngle = atan2(down.position.y - cy, down.position.x - cx)

                    val liveDur = durationMsFlow.value
                    val totalDurMs = if (latestIsCurrent && liveDur > 0L) {
                        liveDur
                    } else {
                        latestFallbackDur.coerceAtLeast(0L)
                    }
                    // Один полный оборот пальцем (360°) перематывает примерно 20 секунд (или ~12% длинного трека)
                    val msPerFullTurn = if (totalDurMs > 0L) {
                        (totalDurMs * 0.12f).coerceIn(14_000f, 30_000f)
                    } else {
                        20_000f
                    }

                    var scrubbedMs = if (latestIsCurrent) {
                        positionMsFlow.value.coerceIn(0L, max(totalDurMs, 0L))
                    } else {
                        0L
                    }
                    var didRotate = false
                    var lastLiveSeekUptime = 0L

                    isFingerScratching = true
                    try {
                        drag(down.id) { change ->
                            val dx = change.position.x - cx
                            val dy = change.position.y - cy
                            if (hypot(dx, dy) >= minDeadZonePx) {
                                val curAngle = atan2(dy, dx)
                                var dRad = curAngle - prevAngle
                                if (dRad > PI.toFloat()) dRad -= twoPi
                                if (dRad < -PI.toFloat()) dRad += twoPi
                                prevAngle = curAngle

                                if (abs(dRad) > 0.001f) {
                                    didRotate = true
                                    change.consume()

                                    // Вращаем пластинку ровно за пальцем
                                    val nextDeg = (rotationDeg.floatValue + dRad * radToDeg) % 360f
                                    rotationDeg.floatValue = if (nextDeg < 0f) nextDeg + 360f else nextDeg

                                    if (totalDurMs > 0L) {
                                        val deltaMs = ((dRad / twoPi) * msPerFullTurn).toLong()
                                        scrubbedMs = (scrubbedMs + deltaMs).coerceIn(0L, totalDurMs)
                                        latestOnPreview(scrubbedMs)

                                        val now = SystemClock.uptimeMillis()
                                        if (now - lastLiveSeekUptime >= 85L) {
                                            lastLiveSeekUptime = now
                                            latestOnCommitSeek(scrubbedMs)
                                        }
                                    }
                                }
                            }
                        }
                    } finally {
                        isFingerScratching = false
                        if (didRotate && totalDurMs > 0L) {
                            latestOnCommitSeek(scrubbedMs)
                        }
                        latestOnPreview(null)
                    }
                }
            }
            .graphicsLayer {
                rotationZ = rotationDeg.floatValue
            }
            .drawWithCache {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val outerR = size.minDimension / 2f
                val grooveStrokePx = 1.4.dp.toPx()
                val rimStrokePx = 1.6.dp.toPx()

                // Острый зигзагообразный участок внизу пластинки (PI / 2), по ширине равный кругу в центре пластинки
                val zigzagCenterAngle = (PI / 2.0).toFloat()
                val zigzagHalfSpan = 0.48f

                val grooveCount = 6
                val groovePaths = Array(grooveCount) { idx ->
                    val baseR = outerR * (0.42f + idx * 0.088f)
                    buildSharpSpikyVinylGroove(
                        cx = cx,
                        cy = cy,
                        ringIndex = idx,
                        baseR = baseR,
                        waveAmp = outerR * 0.015f,
                        maxSpikeAmp = outerR * 0.036f,
                        zigzagCenterRad = zigzagCenterAngle,
                        zigzagHalfSpanRad = zigzagHalfSpan,
                        phaseOffset = idx * 0.35f
                    )
                }

                val centerHaloBrush = Brush.radialGradient(
                    0.0f to accentColor.copy(alpha = 0.80f),
                    0.55f to glowColor.copy(alpha = 0.50f),
                    0.82f to glowColor.copy(alpha = 0.18f),
                    1.0f to Color.Transparent,
                    center = Offset(cx, cy),
                    radius = outerR * 0.42f
                )

                val rimBrush = Brush.sweepGradient(
                    0.0f to SharyMilk.copy(alpha = 0.06f),
                    0.18f to SharyMilk.copy(alpha = 0.24f),
                    0.36f to SharyMilk.copy(alpha = 0.05f),
                    0.68f to SharyMilk.copy(alpha = 0.20f),
                    1.0f to SharyMilk.copy(alpha = 0.06f),
                    center = Offset(cx, cy)
                )

                onDrawBehind {
                    drawCircle(
                        color = discColor,
                        radius = outerR,
                        center = Offset(cx, cy)
                    )

                    drawCircle(
                        brush = rimBrush,
                        radius = outerR - rimStrokePx,
                        center = Offset(cx, cy),
                        style = Stroke(width = rimStrokePx)
                    )

                    // StrokeJoin.Miter с высоким miter создаёт по-настоящему острые пики на зигзаге без скругления вершин
                    val strokeStyle = Stroke(
                        width = grooveStrokePx,
                        cap = StrokeCap.Butt,
                        join = StrokeJoin.Miter,
                        miter = 10f
                    )
                    for (gp in groovePaths) {
                        drawPath(
                            path = gp,
                            color = grooveColor,
                            style = strokeStyle
                        )
                    }

                    drawCircle(
                        brush = centerHaloBrush,
                        radius = outerR * 0.42f,
                        center = Offset(cx, cy)
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(0.33f)
                .clip(CircleShape)
                .border(
                    width = 1.dp,
                    color = SharyMilk.copy(alpha = 0.18f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (coverBitmap != null) {
                Image(
                    bitmap = coverBitmap,
                    contentDescription = songTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val r = size.minDimension / 2f
                            drawCircle(color = discColor)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    0.0f to accentColor.copy(alpha = 0.72f),
                                    0.58f to glowColor.copy(alpha = 0.44f),
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
        }

        // Небольшой тёмный круг строго в центре пластинки
        Box(
            modifier = Modifier
                .fillMaxSize(0.068f)
                .clip(CircleShape)
                .background(discColor)
                .border(
                    width = 1.dp,
                    color = SharyMilk.copy(alpha = 0.22f),
                    shape = CircleShape
                )
        )
    }
}

// Строит замкнутую дорожку пластинки: плавные волны по окружности + острый зубчатый зигзаг-импульс (без плоских полок!)
internal fun buildSharpSpikyVinylGroove(
    cx: Float,
    cy: Float,
    ringIndex: Int,
    baseR: Float,
    waveAmp: Float,
    maxSpikeAmp: Float,
    zigzagCenterRad: Float,
    zigzagHalfSpanRad: Float,
    phaseOffset: Float
): Path {
    val path = Path()
    val twoPi = (2.0 * PI).toFloat()
    val startSmoothTheta = zigzagCenterRad + zigzagHalfSpanRad
    val endSmoothTheta = zigzagCenterRad + twoPi - zigzagHalfSpanRad

    // 1. Плавная волнистая часть по остальной окружности
    val smoothSteps = 136
    for (i in 0..smoothSteps) {
        val frac = i.toFloat() / smoothSteps
        val theta = startSmoothTheta + (endSmoothTheta - startSmoothTheta) * frac
        val r = baseR + waveAmp * sin(18f * theta + phaseOffset)
        val x = cx + r * cos(theta)
        val y = cy + r * sin(theta)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }

    // 2. Острый зигзагообразный сектор: каждая вершина строго чередует верхний и нижний острый пик (V-образные зубцы)
    val spikeVertices = 17
    val zigStart = zigzagCenterRad - zigzagHalfSpanRad
    val zigEnd = zigzagCenterRad + zigzagHalfSpanRad
    // Идём в том же направлении обхода (от endSmoothTheta == zigStart + 2π к startSmoothTheta + 2π)
    for (k in 1 until spikeVertices) {
        val u = k.toFloat() / spikeVertices // 0..1 внутри зоны зигзага
        val theta = zigStart + (zigEnd - zigStart) * u
        val sign = if (k % 2 == 1) -1f else 1f
        // Куполообразная огибающая + разная высота острых зубцов как на эскизе звуковой волны
        val bellEnvelope = sin(u * PI).toFloat()
        val toothVariety = 0.58f + 0.42f * abs(sin(k * 2.15f + ringIndex * 0.95f))
        val spikeOffset = sign * maxSpikeAmp * bellEnvelope * toothVariety
        val r = baseR + spikeOffset
        path.lineTo(cx + r * cos(theta), cy + r * sin(theta))
    }

    path.close()
    return path
}

@Composable
private fun PlayerTimelineSection(
    positionMsFlow: StateFlow<Long>,
    durationMsFlow: StateFlow<Long>,
    externalScratchMs: Long?,
    isSelectedSongCurrent: Boolean,
    fallbackDurationMs: Long,
    onScrubRotateDegrees: (Float) -> Unit,
    onSeekToMs: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val surfaces = SharyThemeTokens.surfaces
    val livePosMs by positionMsFlow.collectAsState()
    val liveDurMs by durationMsFlow.collectAsState()

    val totalDurationMs = if (isSelectedSongCurrent && liveDurMs > 0L) {
        liveDurMs
    } else {
        fallbackDurationMs.coerceAtLeast(0L)
    }

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var barWidthPx by remember { mutableFloatStateOf(1f) }

    val latestTotalDuration by rememberUpdatedState(totalDurationMs)
    val latestOnSeek by rememberUpdatedState(onSeekToMs)
    val latestOnRotate by rememberUpdatedState(onScrubRotateDegrees)

    val currentPosMs = when {
        isDragging -> (dragFraction * totalDurationMs).toLong()
        externalScratchMs != null -> externalScratchMs.coerceIn(0L, max(totalDurationMs, externalScratchMs))
        isSelectedSongCurrent -> livePosMs.coerceIn(0L, max(totalDurationMs, livePosMs))
        else -> 0L
    }

    val progressFraction = if (totalDurationMs > 0L) {
        (currentPosMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val timelineDesc = stringResource(R.string.player_timeline_description)
    val activeThumb = isDragging || externalScratchMs != null

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .semantics { contentDescription = timelineDesc }
                .onSizeChanged { barWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val frac = (offset.x / barWidthPx).coerceIn(0f, 1f)
                        val dur = latestTotalDuration
                        if (dur > 0L) {
                            latestOnSeek((frac * dur).toLong())
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { startOffset ->
                            isDragging = true
                            dragFraction = (startOffset.x / barWidthPx).coerceIn(0f, 1f)
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val prevFrac = dragFraction
                            val nextFrac = (change.position.x / barWidthPx).coerceIn(0f, 1f)
                            dragFraction = nextFrac
                            latestOnRotate((nextFrac - prevFrac) * 720f)
                        },
                        onDragEnd = {
                            val dur = latestTotalDuration
                            if (dur > 0L) {
                                latestOnSeek((dragFraction * dur).toLong())
                            }
                            isDragging = false
                        },
                        onDragCancel = {
                            isDragging = false
                        }
                    )
                }
        ) {
            val trackHeight = 4.2.dp.toPx()
            val thumbRadius = if (activeThumb) 7.5.dp.toPx() else 6.0.dp.toPx()
            val centerY = size.height / 2f
            val activeX = (size.width * progressFraction).coerceIn(0f, size.width)

            drawLine(
                color = surfaces.playerTrackInactive,
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round
            )

            if (activeX > 0.5f) {
                drawLine(
                    color = surfaces.playerAccent,
                    start = Offset(0f, centerY),
                    end = Offset(activeX, centerY),
                    strokeWidth = trackHeight,
                    cap = StrokeCap.Round
                )
            }

            drawCircle(
                color = surfaces.playerAccent,
                radius = thumbRadius,
                center = Offset(activeX, centerY)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatSongDuration(currentPosMs),
                color = surfaces.textSecondary,
                fontSize = 12.sp
            )
            Text(
                text = formatSongDuration(totalDurationMs),
                color = surfaces.textSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun PlayerTransportControlsRow(
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    isRepeatEnabled: Boolean,
    onShuffleClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onRepeatClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val surfaces = SharyThemeTokens.surfaces

    val playInteraction = remember { MutableInteractionSource() }
    val isPlayPressed by playInteraction.collectIsPressedAsState()
    val playScaleState = rememberSharyInteractiveScaleState(
        isSelected = isPlaying,
        isPressed = isPlayPressed,
        selectedScale = 1.04f,
        pressedScale = 1.12f
    )
    val playBgState = animateColorAsState(
        targetValue = if (isPlayPressed) {
            surfaces.playerAccent.copy(alpha = 0.82f)
        } else {
            surfaces.playerAccent
        },
        animationSpec = tween(110),
        label = "playerPlayCircleBg"
    )
    val playLabel = if (isPlaying) {
        stringResource(R.string.action_pause)
    } else {
        stringResource(R.string.action_play)
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        // Боковая кнопка слева: случайный порядок (опущена чуть ниже относительно центрального ряда как на фото)
        val shuffleTint by animateColorAsState(
            targetValue = if (isShuffleEnabled) surfaces.transportPrimaryIcon else surfaces.transportSecondaryIcon,
            animationSpec = tween(120),
            label = "playerShuffleTint"
        )
        PlayerSmallIconButton(
            label = stringResource(R.string.action_shuffle),
            isSelected = isShuffleEnabled,
            touchSize = 42.dp,
            onClick = onShuffleClick,
            modifier = Modifier.padding(top = 24.dp)
        ) {
            SharyShuffleIcon(
                tint = shuffleTint,
                modifier = Modifier.size(22.dp)
            )
        }

        // Перемотка к предыдущему треку
        PlayerSmallIconButton(
            label = stringResource(R.string.action_previous),
            isSelected = false,
            touchSize = 42.dp,
            onClick = onPreviousClick,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            SharySkipPreviousIcon(
                tint = surfaces.transportPrimaryIcon,
                modifier = Modifier.size(24.dp)
            )
        }

        // Центральная круглая кнопка Play / Pause
        Box(
            modifier = Modifier
                .size(SharyDimens.PlayerPlayButtonSize)
                .graphicsLayer {
                    val s = playScaleState.value
                    scaleX = s
                    scaleY = s
                }
                .semantics {
                    contentDescription = playLabel
                    role = Role.Button
                }
                .clip(CircleShape)
                .drawBehind {
                    drawRect(color = playBgState.value)
                }
                .clickable(
                    interactionSource = playInteraction,
                    indication = null,
                    onClick = onPlayPauseClick
                ),
            contentAlignment = Alignment.Center
        ) {
            SharyPlayPauseIcon(
                isPlaying = isPlaying,
                tint = surfaces.onAccent,
                modifier = Modifier.size(26.dp)
            )
        }

        // Перемотка к следующему треку
        PlayerSmallIconButton(
            label = stringResource(R.string.action_next),
            isSelected = false,
            touchSize = 42.dp,
            onClick = onNextClick,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            SharySkipNextIcon(
                tint = surfaces.transportPrimaryIcon,
                modifier = Modifier.size(24.dp)
            )
        }

        // Боковая кнопка справа: цикличность песни (опущена симметрично левой кнопке как на фото)
        val repeatTint by animateColorAsState(
            targetValue = if (isRepeatEnabled) surfaces.transportPrimaryIcon else surfaces.transportSecondaryIcon,
            animationSpec = tween(120),
            label = "playerRepeatTint"
        )
        PlayerSmallIconButton(
            label = stringResource(R.string.action_repeat),
            isSelected = isRepeatEnabled,
            touchSize = 42.dp,
            onClick = onRepeatClick,
            modifier = Modifier.padding(top = 24.dp)
        ) {
            SharyRepeatIcon(
                tint = repeatTint,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun PlayerSmallIconButton(
    label: String,
    isSelected: Boolean,
    touchSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconContent: @Composable () -> Unit
) {
    val surfaces = SharyThemeTokens.surfaces
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scaleState = rememberSharyInteractiveScaleState(
        isSelected = isSelected,
        isPressed = isPressed
    )
    val circleColorState = animateColorAsState(
        targetValue = when {
            isSelected -> surfaces.navSelectionCircleSelected
            isPressed -> surfaces.navSelectionCirclePressed
            else -> Color.Transparent
        },
        animationSpec = tween(110),
        label = "playerBtnCircle"
    )

    Box(
        modifier = modifier
            .size(touchSize)
            .semantics {
                contentDescription = label
                role = Role.Button
                selected = isSelected
            }
            .drawBehind {
                drawCircle(
                    color = circleColorState.value,
                    radius = size.minDimension / 2f
                )
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                val s = scaleState.value
                scaleX = s
                scaleY = s
            },
            contentAlignment = Alignment.Center
        ) {
            iconContent()
        }
    }
}

@Composable
internal fun MiniSongPlateCard(
    title: String,
    coverBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    isCentered: Boolean,
    isPlayingPlate: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDeleteClick: (() -> Unit)? = null
) {
    val surfaces = SharyThemeTokens.surfaces
    val shape = RoundedCornerShape(SharyDimens.PlayerMiniPlateCornerRadius)
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val pressScaleState = rememberSharyInteractiveScaleState(
        isSelected = false,
        isPressed = isPressed,
        pressedScale = 1.06f
    )

    val plateBrush = remember(isCentered, surfaces.isLight) {
        if (surfaces.isLight) {
            if (isCentered) {
                Brush.linearGradient(
                    colors = listOf(
                        lerp(SharyMilk, SharyBlack, 0.17f).copy(alpha = 0.94f),
                        lerp(SharyMilk, SharyBlack, 0.09f).copy(alpha = 0.90f)
                    )
                )
            } else {
                Brush.linearGradient(
                    colors = listOf(
                        lerp(SharyMilk, SharyBlack, 0.10f).copy(alpha = 0.82f),
                        lerp(SharyMilk, SharyBlack, 0.05f).copy(alpha = 0.76f)
                    )
                )
            }
        } else {
            if (isCentered) {
                Brush.linearGradient(
                    colors = listOf(
                        lerp(SharyBlack, SharyMilk, 0.34f).copy(alpha = 0.90f),
                        lerp(SharyBlack, SharyMilk, 0.56f).copy(alpha = 0.84f)
                    )
                )
            } else {
                Brush.linearGradient(
                    colors = listOf(
                        lerp(SharyBlack, SharyMilk, 0.24f).copy(alpha = 0.76f),
                        lerp(SharyBlack, SharyMilk, 0.10f).copy(alpha = 0.72f)
                    )
                )
            }
        }
    }

    val borderColor = when {
        isPlayingPlate -> surfaces.textPrimary.copy(alpha = 0.55f)
        isCentered -> surfaces.textPrimary.copy(alpha = 0.28f)
        else -> surfaces.songRowBorder
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                val s = pressScaleState.value
                scaleX = s
                scaleY = s
            }
            .clip(shape)
            .background(brush = plateBrush)
            .border(
                width = SharyDimens.GlassBorderWidth,
                color = borderColor,
                shape = shape
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .weight(1f, fill = false)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(surfaces.placeholderCoverFill),
                contentAlignment = Alignment.Center
            ) {
                if (coverBitmap != null) {
                    Image(
                        bitmap = coverBitmap,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Статичный серый логотип SHARY, если у песни нет собственного фото
                    SharyLogoMark(
                        primaryColor = surfaces.placeholderLogoPrimary,
                        secondaryColor = surfaces.placeholderLogoSecondary,
                        modifier = Modifier.fillMaxSize(0.64f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            RightToLeftFadingSongTitle(
                title = title,
                textColor = surfaces.textPrimary.copy(alpha = 0.94f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = if (onDeleteClick != null) 24.dp else 0.dp)
                    .height(17.dp)
            )
        }

        // Кружочек удаления песни с мусоркой справа снизу внутри пластинки (используется в Профиле)
        if (onDeleteClick != null) {
            val delDesc = stringResource(R.string.action_delete_song)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(22.dp)
                    .semantics {
                        contentDescription = delDesc
                        role = Role.Button
                    }
                    .clip(CircleShape)
                    .background(surfaces.playerAccent.copy(alpha = 0.92f))
                    .border(
                        width = 0.8.dp,
                        color = surfaces.onAccent.copy(alpha = 0.28f),
                        shape = CircleShape
                    )
                    .clickable(onClick = onDeleteClick),
                contentAlignment = Alignment.Center
            ) {
                SharyTrashIcon(
                    tint = surfaces.onAccent,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun RightToLeftFadingSongTitle(
    title: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val speedPxPerSec = with(density) { 26.dp.toPx() }

    var boxWidthPx by remember { mutableFloatStateOf(0f) }
    var textWidthPx by remember { mutableFloatStateOf(0f) }
    val offsetX = remember(title) { mutableFloatStateOf(0f) }

    LaunchedEffect(title, boxWidthPx, textWidthPx) {
        if (boxWidthPx <= 1f || textWidthPx <= 1f) return@LaunchedEffect
        offsetX.floatValue = boxWidthPx * 0.16f
        val wrapRight = boxWidthPx
        val endLeft = -textWidthPx

        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dt = ((frameNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    val next = offsetX.floatValue - speedPxPerSec * dt
                    offsetX.floatValue = if (next < endLeft) wrapRight else next
                }
                lastNanos = frameNanos
            }
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { boxWidthPx = it.width.toFloat() }
            .clipToBounds()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.horizontalGradient(
                        0.0f to Color.Transparent,
                        0.18f to Color.Black,
                        0.84f to Color.Black,
                        1.0f to Color.Transparent
                    ),
                    blendMode = BlendMode.DstIn
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = title,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            modifier = Modifier
                .wrapContentWidth(unbounded = true, align = Alignment.Start)
                .onSizeChanged { textWidthPx = it.width.toFloat() }
                .graphicsLayer {
                    translationX = offsetX.floatValue
                }
        )
    }
}
