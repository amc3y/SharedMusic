package com.example.sharedmusic.design.screens

import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sharedmusic.design.components.SharyBackgroundArea
import com.example.sharedmusic.design.components.SharyBottomArea
import com.example.sharedmusic.design.components.SharyLibraryContent
import com.example.sharedmusic.design.components.SharyMiddleArea
import com.example.sharedmusic.design.components.SharyRoomTransportRow
import com.example.sharedmusic.design.components.SharyTopArea
import com.example.sharedmusic.design.components.SharyVinylPlayerContent
import com.example.sharedmusic.design.navigation.SharyTab
import com.example.sharedmusic.design.theme.SharyDimens
import com.example.sharedmusic.design.theme.SharyTheme
import com.example.sharedmusic.design.theme.SharyThemeTokens
import com.example.sharedmusic.logic.model.Song
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Главный каркас интерфейса SHARY (палитра Milk #FFFDF4 + Black #222323 с поддержкой Тёмной, Серой и Светлой тем):
// связывает Библиотеку, Виниловый плеер, Комнату совместного прослушивания, Настройки и Профиль
@Composable
fun SharyShellScreen(
    selectedTab: SharyTab,
    onTabSelect: (SharyTab) -> Unit,
    onAddSongsClick: () -> Unit,
    modifier: Modifier = Modifier,
    songs: List<Song> = emptyList(),
    currentSongId: String? = null,
    playerSelectedSongId: String? = null,
    isLibraryPlayerOpen: Boolean = false,
    favoriteSongIds: Set<String> = emptySet(),
    isSearchActive: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    recentSearches: List<String> = emptyList(),
    onSelectRecentSearch: (String) -> Unit = {},
    onRemoveRecentSearch: (String) -> Unit = {},
    onClearRecentSearches: () -> Unit = {},
    onCommitSearchEntry: (String) -> Unit = {},
    onCloseSearch: () -> Unit = {},
    onCollapseLibraryPlayer: () -> Unit = {},
    onSelectPlayerSongOnly: (Song) -> Unit = {},
    onPlaySpecificSong: (Song) -> Unit = {},
    onPlayPauseSelectedSong: (Song) -> Unit = {},
    onSeekSelectedSong: (Song, Long) -> Unit = { _, _ -> },
    positionMsFlow: StateFlow<Long> = remember { MutableStateFlow(0L) },
    durationMsFlow: StateFlow<Long> = remember { MutableStateFlow(0L) },
    onTopLogoClick: () -> Unit = {},
    onTopSearchClick: () -> Unit = {},
    activeRoomCode: String? = null,
    onExitRoomClick: () -> Unit = {},
    onCopyRoomCodeClick: () -> Unit = {},
    onShareRoomCodeClick: () -> Unit = {},
    globalBannerNotice: String? = null,
    onDismissBannerNotice: () -> Unit = {},
    isPlaying: Boolean = false,
    isShuffleEnabled: Boolean = false,
    isRepeatEnabled: Boolean = false,
    vibeMotionMultiplier: Float = 1f,
    externalReactionTrigger: Int = 0,
    onSongClick: (Song) -> Unit = {},
    onDeleteSong: (Song) -> Unit = {},
    onPlayPauseClick: () -> Unit = {},
    onPreviousClick: () -> Unit = {},
    onNextClick: () -> Unit = {},
    onShuffleClick: () -> Unit = {},
    onRepeatClick: () -> Unit = {},
    onSeekToMs: (Long) -> Unit = {},
    onToggleFavoriteSong: (String) -> Unit = {},
    topCenterContent: @Composable BoxScope.() -> Unit = {},
    roomParticipantsStrip: @Composable () -> Unit = {},
    roomMiddleContent: @Composable (triggerVibeReaction: () -> Unit) -> Unit = {},
    middleContent: @Composable BoxScope.(triggerVibeReaction: () -> Unit) -> Unit = {}
) {
    val surfaces = SharyThemeTokens.surfaces
    val isRoomTab = selectedTab == SharyTab.ROOM
    val isInActiveRoom = isRoomTab && activeRoomCode != null
    val showVinylPlayerInLibrary =
        selectedTab == SharyTab.LIBRARY && isLibraryPlayerOpen && songs.isNotEmpty()

    // На вкладке Настроек (3-я вкладка) и в лобби Комнаты центральный круг "+" и впадина убираются;
    // в Библиотеке, Профиле и активной Комнате — отображаются.
    val showBottomCenterCircleAndNotch = when (selectedTab) {
        SharyTab.ROOM -> isInActiveRoom
        SharyTab.SETTINGS -> false
        SharyTab.LIBRARY, SharyTab.PROFILE -> true
    }

    BackHandler(enabled = selectedTab == SharyTab.LIBRARY && (isLibraryPlayerOpen || isSearchActive)) {
        if (isLibraryPlayerOpen) {
            onCollapseLibraryPlayer()
        } else if (isSearchActive) {
            onCloseSearch()
        }
    }

    var internalReactionCount by remember { mutableIntStateOf(0) }
    var lastSeekReactionUptime by remember { mutableLongStateOf(0L) }
    var isFirstPlayEmission by remember { mutableStateOf(true) }

    LaunchedEffect(isPlaying) {
        if (isFirstPlayEmission) {
            isFirstPlayEmission = false
        } else {
            internalReactionCount += 1
        }
    }

    val combinedReactionTrigger = internalReactionCount + externalReactionTrigger
    val triggerVibeReaction: () -> Unit = { internalReactionCount += 1 }

    // Верхний отступ средней области: в активной Комнате опускается ниже под полосу кружков участников
    val middleTopInsetState = animateDpAsState(
        targetValue = if (isInActiveRoom) {
            SharyDimens.ScreenOuterMargin +
                SharyDimens.TopAreaHeight +
                SharyDimens.RoomParticipantsStripHeight +
                8.dp
        } else {
            SharyDimens.ScreenOuterMargin + SharyDimens.TopAreaHeight + SharyDimens.TopToMiddleGap
        },
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "middleAreaTopInset"
    )

    // Нижний отступ средней области
    val middleBottomInsetState = animateDpAsState(
        targetValue = when {
            isInActiveRoom -> SharyDimens.MiddleBottomInsetRoom
            isRoomTab -> SharyDimens.MiddleBottomInsetRoomLobby
            else -> SharyDimens.MiddleBottomInsetDefault
        },
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "middleAreaBottomInset"
    )

    SharyBackgroundArea(
        modifier = modifier,
        isPlaying = isPlaying,
        reactionTrigger = combinedReactionTrigger,
        motionMultiplier = vibeMotionMultiplier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            // 1-я область: Верхняя панель
            SharyTopArea(
                modifier = Modifier.align(Alignment.TopCenter),
                isSearchActive = selectedTab == SharyTab.LIBRARY && !showVinylPlayerInLibrary && isSearchActive,
                activeRoomCode = if (isInActiveRoom) activeRoomCode else null,
                onLogoClick = {
                    internalReactionCount += 1
                    onTopLogoClick()
                },
                onSearchClick = {
                    internalReactionCount += 1
                    onTopSearchClick()
                },
                onExitRoomClick = {
                    internalReactionCount += 1
                    onExitRoomClick()
                },
                onCopyRoomCodeClick = onCopyRoomCodeClick,
                onShareRoomCodeClick = onShareRoomCodeClick,
                centerContent = topCenterContent
            )

            // Полоса кружков участников комнаты над средней областью (в активной комнате)
            AnimatedVisibility(
                visible = isInActiveRoom,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(140)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = SharyDimens.ScreenOuterMargin + SharyDimens.TopAreaHeight + 4.dp)
            ) {
                roomParticipantsStrip()
            }

            // 2-я область: Средняя панель (острый правый верхний угол)
            SharyMiddleArea(
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .layout { measurable, constraints ->
                        val topPx = middleTopInsetState.value.roundToPx()
                        val bottomPx = middleBottomInsetState.value.roundToPx()
                        val targetHeight = (constraints.maxHeight - topPx - bottomPx).coerceAtLeast(0)
                        val placeable = measurable.measure(
                            constraints.copy(
                                minHeight = targetHeight,
                                maxHeight = targetHeight
                            )
                        )
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.placeRelative(0, topPx)
                        }
                    },
                content = {
                    when (selectedTab) {
                        SharyTab.LIBRARY -> {
                            Crossfade(
                                targetState = showVinylPlayerInLibrary,
                                animationSpec = tween(durationMillis = 180),
                                label = "libraryListToVinylPlayer"
                            ) { playerMode ->
                                if (playerMode) {
                                    SharyVinylPlayerContent(
                                        songs = songs,
                                        selectedSongId = playerSelectedSongId,
                                        currentSongId = currentSongId,
                                        favoriteSongIds = favoriteSongIds,
                                        isPlaying = isPlaying,
                                        isShuffleEnabled = isShuffleEnabled,
                                        isRepeatEnabled = isRepeatEnabled,
                                        positionMsFlow = positionMsFlow,
                                        durationMsFlow = durationMsFlow,
                                        onCollapseToList = {
                                            internalReactionCount += 1
                                            onCollapseLibraryPlayer()
                                        },
                                        onSelectSongOnly = onSelectPlayerSongOnly,
                                        onPlaySpecificSong = { song ->
                                            internalReactionCount += 1
                                            onPlaySpecificSong(song)
                                        },
                                        onPlayPauseSelected = { song ->
                                            internalReactionCount += 1
                                            onPlayPauseSelectedSong(song)
                                        },
                                        onPreviousClick = {
                                            internalReactionCount += 1
                                            onPreviousClick()
                                        },
                                        onNextClick = {
                                            internalReactionCount += 1
                                            onNextClick()
                                        },
                                        onShuffleClick = onShuffleClick,
                                        onRepeatClick = onRepeatClick,
                                        onSeekSelectedSong = { song, targetMs ->
                                            val now = SystemClock.uptimeMillis()
                                            if (now - lastSeekReactionUptime > 360L) {
                                                lastSeekReactionUptime = now
                                                internalReactionCount += 1
                                            }
                                            onSeekSelectedSong(song, targetMs)
                                        },
                                        onToggleFavorite = { song ->
                                            internalReactionCount += 1
                                            onToggleFavoriteSong(song.id)
                                        }
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(SharyDimens.PanelInnerPadding)
                                    ) {
                                        SharyLibraryContent(
                                            songs = songs,
                                            currentSongId = currentSongId,
                                            favoriteSongIds = favoriteSongIds,
                                            isSearchActive = isSearchActive,
                                            searchQuery = searchQuery,
                                            onSearchQueryChange = onSearchQueryChange,
                                            recentSearches = recentSearches,
                                            onSelectRecentSearch = onSelectRecentSearch,
                                            onRemoveRecentSearch = onRemoveRecentSearch,
                                            onClearRecentSearches = onClearRecentSearches,
                                            onCommitSearchEntry = onCommitSearchEntry,
                                            onSongClick = { song ->
                                                internalReactionCount += 1
                                                onSongClick(song)
                                            },
                                            onToggleFavorite = { song ->
                                                internalReactionCount += 1
                                                onToggleFavoriteSong(song.id)
                                            },
                                            onDeleteSong = onDeleteSong
                                        )
                                    }
                                }
                            }
                        }

                        SharyTab.ROOM -> {
                            roomMiddleContent(triggerVibeReaction)
                        }

                        SharyTab.PROFILE -> {
                            // Без горизонтальных отступов панели, чтобы нижний арочный слайдер песен шёл от края до края
                            Box(modifier = Modifier.fillMaxSize()) {
                                middleContent(triggerVibeReaction)
                            }
                        }

                        SharyTab.SETTINGS -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(SharyDimens.PanelInnerPadding)
                            ) {
                                middleContent(triggerVibeReaction)
                            }
                        }
                    }
                }
            )

            // Полоса из 4 значков механики музыки — только в активной Комнате
            AnimatedVisibility(
                visible = isInActiveRoom,
                enter = fadeIn(animationSpec = tween(durationMillis = 200)),
                exit = fadeOut(animationSpec = tween(durationMillis = 150)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom = SharyDimens.ScreenOuterMargin + SharyDimens.BottomAreaBarHeight
                    )
            ) {
                SharyRoomTransportRow(
                    isShuffleEnabled = isShuffleEnabled,
                    isRepeatEnabled = isRepeatEnabled,
                    onShuffleClick = onShuffleClick,
                    onPreviousClick = {
                        internalReactionCount += 1
                        onPreviousClick()
                    },
                    onNextClick = {
                        internalReactionCount += 1
                        onNextClick()
                    },
                    onRepeatClick = onRepeatClick
                )
            }

            // 3-я область: Нижняя панель навигации
            SharyBottomArea(
                selectedTab = selectedTab,
                onTabSelect = onTabSelect,
                onAddSongsClick = onAddSongsClick,
                onPlayPauseClick = {
                    internalReactionCount += 1
                    onPlayPauseClick()
                },
                isPlaying = isPlaying,
                showCenterButtonAndNotch = showBottomCenterCircleAndNotch,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            // Глобальное уведомление
            if (globalBannerNotice != null) {
                LaunchedEffect(globalBannerNotice) {
                    delay(2_600L)
                    onDismissBannerNotice()
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(
                            top = SharyDimens.ScreenOuterMargin + SharyDimens.TopAreaHeight + 12.dp,
                            start = 24.dp,
                            end = 24.dp
                        )
                        .clip(RoundedCornerShape(10.dp))
                        .background(surfaces.dialogFill)
                        .border(1.dp, surfaces.dialogBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = globalBannerNotice,
                        color = surfaces.textPrimary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Preview(
    name = "SHARY Vinyl Player Monochrome",
    showBackground = true,
    widthDp = 393,
    heightDp = 851
)
@Composable
private fun SharyShellVinylPlayerPreview() {
    val sampleSongs = remember {
        listOf(
            Song(id = "1", title = "Midnight Monochrome", uri = Uri.EMPTY, durationMs = 214_000L),
            Song(id = "2", title = "Milk & Shadow Resonance", uri = Uri.EMPTY, durationMs = 184_000L),
            Song(id = "3", title = "Echoes of Silence", uri = Uri.EMPTY, durationMs = 246_000L)
        )
    }
    var selectedTab by remember { mutableStateOf(SharyTab.LIBRARY) }
    var playerOpen by remember { mutableStateOf(true) }
    var selectedId by remember { mutableStateOf<String?>("2") }
    var activeSongId by remember { mutableStateOf<String?>("2") }
    var favorites by remember { mutableStateOf(setOf("2")) }
    var playing by remember { mutableStateOf(false) }

    SharyTheme(darkTheme = true) {
        SharyShellScreen(
            selectedTab = selectedTab,
            onTabSelect = { tab -> selectedTab = tab },
            onAddSongsClick = {},
            songs = sampleSongs,
            currentSongId = activeSongId,
            playerSelectedSongId = selectedId,
            isLibraryPlayerOpen = playerOpen,
            favoriteSongIds = favorites,
            isPlaying = playing,
            onCollapseLibraryPlayer = { playerOpen = false },
            onSelectPlayerSongOnly = { s -> selectedId = s.id },
            onPlaySpecificSong = { s ->
                selectedId = s.id
                activeSongId = s.id
                playing = true
            },
            onPlayPauseSelectedSong = { s ->
                if (activeSongId == s.id) playing = !playing
                else {
                    activeSongId = s.id
                    playing = true
                }
            },
            onSongClick = { clicked ->
                selectedId = clicked.id
                activeSongId = clicked.id
                playing = false
                playerOpen = true
            },
            onToggleFavoriteSong = { id ->
                favorites = if (id in favorites) favorites - id else favorites + id
            }
        )
    }
}
