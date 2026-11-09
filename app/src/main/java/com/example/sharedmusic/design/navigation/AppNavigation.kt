package com.example.sharedmusic.design.navigation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sharedmusic.design.components.SharyAdminSelfSettingsDialog
import com.example.sharedmusic.design.components.SharyKickParticipantConfirmDialog
import com.example.sharedmusic.design.components.SharyLeaveRoomConfirmDialog
import com.example.sharedmusic.design.components.SharyManageParticipantDialog
import com.example.sharedmusic.design.components.SharyProfileContent
import com.example.sharedmusic.design.components.SharyRoomChatDialog
import com.example.sharedmusic.design.components.SharyRoomMiddleContent
import com.example.sharedmusic.design.components.SharyRoomParticipantsStrip
import com.example.sharedmusic.design.components.SharySettingsContent
import com.example.sharedmusic.design.room.rememberRoomExtendedController
import com.example.sharedmusic.design.screens.SharyShellScreen
import com.example.sharedmusic.design.theme.SharyTheme
import com.example.sharedmusic.logic.model.RoomDevice
import com.example.sharedmusic.logic.model.RoomRole
import com.example.sharedmusic.logic.model.RoomTrack
import com.example.sharedmusic.logic.model.Song
import com.example.sharedmusic.logic.room.RoomController
import com.example.sharedmusic.logic.viewmodel.MusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

// Корневая точка входа UI SHARY:
// - 3 темы оформления: Тёмная, Серая (текущая) и Светлая на базе Milk (#FFFDF4) + Black (#222323);
// - защита от дубликатов, фильтр «Только безопасные MP3», режим «Только по Wi-Fi», работающий эквалайзер и таймер сна;
// - полная связка с RoomController и MusicViewModel партнёра.
@Composable
fun AppNavigation() {
    val activityContext = LocalContext.current
    val appContext = activityContext.applicationContext
    val rootView = LocalView.current
    val musicViewModel: MusicViewModel = viewModel()
    val coroutineScope = rememberCoroutineScope()

    val connectionManager = remember { RoomController(appContext) }

    var selectedTab by rememberSaveable { mutableStateOf(SharyTab.LIBRARY) }
    var isShuffleEnabled by rememberSaveable { mutableStateOf(false) }
    var isRepeatEnabled by rememberSaveable { mutableStateOf(false) }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isLibraryPlayerOpen by rememberSaveable { mutableStateOf(false) }
    var playerSelectedSongId by rememberSaveable { mutableStateOf<String?>(null) }

    var favoriteSongIds by rememberSaveable {
        mutableStateOf(FavoriteSongsStore.loadFavoriteIds(appContext))
    }
    var savedRemoteSongIds by rememberSaveable {
        mutableStateOf(FavoriteSongsStore.loadSavedRemoteIds(appContext))
    }
    var recentSearches by rememberSaveable {
        mutableStateOf(FavoriteSongsStore.loadRecentSearches(appContext))
    }
    var localProfile by remember {
        mutableStateOf(UserProfileStore.loadProfile(appContext))
    }
    var appSettings by remember {
        mutableStateOf(AppSettingsStore.load(appContext))
    }
    var sleepTimerRemainingSec by rememberSaveable { mutableIntStateOf(0) }
    var tempCacheSizeBytes by remember { mutableLongStateOf(0L) }
    var isWifiConnectedNow by remember { mutableStateOf(AppSettingsStore.isWifiConnected(appContext)) }

    // Флаг: выбор песен запущен кнопкой "+" внутри активной Комнаты -> после импорта сразу отправляем в комнату
    var pendingShareAfterPick by remember { mutableStateOf(false) }

    var globalBannerNotice by remember { mutableStateOf<String?>(null) }
    var externalReactionTrigger by rememberSaveable { mutableIntStateOf(0) }

    // Состояния модальных окон Комнаты
    var showAdminSelfSettings by remember { mutableStateOf(false) }
    var managedParticipant by remember { mutableStateOf<RoomDevice?>(null) }
    var kickConfirmParticipant by remember { mutableStateOf<RoomDevice?>(null) }
    var showLeaveRoomConfirm by remember { mutableStateOf(false) }

    val favoriteSongIdSet = remember(favoriteSongIds) { favoriteSongIds.toSet() }
    val savedRemoteIdSet = remember(savedRemoteSongIds) { savedRemoteSongIds.toSet() }

    val allSongs by musicViewModel.songs.collectAsState()
    val currentSong by musicViewModel.currentSong.collectAsState()
    val isPlaying by musicViewModel.isPlaying.collectAsState()
    val activeRoomCode = connectionManager.currentRoomCode

    // Личная библиотека пользователя: локально добавленные песни + песни, скачанные пользователем кнопкой ⤓ в Комнате
    val userLibrarySongs = remember(allSongs, savedRemoteIdSet) {
        allSongs.filter { song -> !song.isRemote || song.id in savedRemoteIdSet }
    }

    val updateSettings: (AppSettingsState) -> Unit = { updated ->
        appSettings = updated
        AppSettingsStore.save(appContext, updated)
    }

    // Удержание подсветки экрана во время активной Комнаты (если включено в Настройках)
    val shouldKeepScreenOn = activeRoomCode != null && appSettings.keepScreenOnInRoom
    DisposableEffect(rootView, shouldKeepScreenOn) {
        val prev = rootView.keepScreenOn
        rootView.keepScreenOn = shouldKeepScreenOn
        onDispose {
            rootView.keepScreenOn = prev
        }
    }

    // Живой отсчёт таймера сна с автоматической паузой воспроизведения
    LaunchedEffect(sleepTimerRemainingSec > 0) {
        while (sleepTimerRemainingSec > 0) {
            delay(1_000L)
            sleepTimerRemainingSec -= 1
            if (sleepTimerRemainingSec == 0) {
                if (musicViewModel.isPlaying.value) {
                    musicViewModel.toggleMusic()
                }
                globalBannerNotice = "Таймер сна завершён — воспроизведение поставлено на паузу"
            }
        }
    }

    // Актуализируем размер временного кэша и статус Wi-Fi при открытии Настроек
    LaunchedEffect(selectedTab, connectionManager.isBusy) {
        if (selectedTab == SharyTab.SETTINGS) {
            isWifiConnectedNow = AppSettingsStore.isWifiConnected(appContext)
            tempCacheSizeBytes = withContext(Dispatchers.IO) {
                AppSettingsStore.calculateTempCacheBytes(appContext)
            }
        }
    }

    // Подключаем реальный системный эквалайзер, усиление баса, громкость и скорость к ExoPlayer
    SharyAudioEffectsBridge(
        musicViewModel = musicViewModel,
        settings = appSettings,
        isInActiveRoom = activeRoomCode != null
    )

    val cleanupTemporaryUnsavedRoomSongs: () -> Unit = {
        val snapshot = musicViewModel.songs.value
        val savedSet = FavoriteSongsStore.loadSavedRemoteIds(appContext).toSet()
        snapshot.filter { it.isRemote && it.id !in savedSet }.forEach { tempSong ->
            musicViewModel.removeSong(tempSong)
        }
    }

    val extendedRoom = rememberRoomExtendedController(
        roomController = connectionManager,
        localProfile = localProfile,
        onForcedExit = { reason ->
            cleanupTemporaryUnsavedRoomSongs()
            globalBannerNotice = reason
        }
    )

    PartnerPlaybackBridge(
        musicViewModel = musicViewModel,
        connectionManager = connectionManager,
        userLibrarySongs = userLibrarySongs,
        isRepeatEnabled = isRepeatEnabled,
        isShuffleEnabled = isShuffleEnabled,
        wifiOnlyTransfers = appSettings.wifiOnlyTransfers,
        onlySafeMp3Files = appSettings.onlySafeMp3Files,
        onBlockedNotice = { msg -> extendedRoom.toastNotice = msg }
    )

    BackHandler(enabled = selectedTab == SharyTab.ROOM && activeRoomCode != null) {
        if (extendedRoom.isChatOpen) {
            extendedRoom.closeChat()
        } else {
            showLeaveRoomConfirm = true
        }
    }

    val recordRecent: (String) -> Unit = { rawText ->
        val updated = FavoriteSongsStore.recordRecentSearch(recentSearches, rawText)
        if (updated != recentSearches) {
            recentSearches = updated
            FavoriteSongsStore.saveRecentSearches(appContext, updated)
        }
    }

    val closeSearchCleanly: () -> Unit = {
        if (searchQuery.isNotBlank()) {
            recordRecent(searchQuery)
        }
        isSearchActive = false
        searchQuery = ""
    }

    val markRemoteSongSavedToLibrary: (String) -> Unit = { songId ->
        if (songId.isNotBlank() && songId !in savedRemoteSongIds) {
            val updated = savedRemoteSongIds + songId
            savedRemoteSongIds = updated
            FavoriteSongsStore.saveSavedRemoteIds(appContext, updated)
        }
    }

    LaunchedEffect(currentSong?.id) {
        val activeId = currentSong?.id
        if (activeId != null && userLibrarySongs.any { it.id == activeId }) {
            playerSelectedSongId = activeId
        }
    }

    LaunchedEffect(userLibrarySongs.size) {
        if (userLibrarySongs.isEmpty()) {
            isLibraryPlayerOpen = false
            playerSelectedSongId = null
        } else if (playerSelectedSongId != null && userLibrarySongs.none { it.id == playerSelectedSongId }) {
            playerSelectedSongId = currentSong?.id?.takeIf { id -> userLibrarySongs.any { it.id == id } }
                ?: userLibrarySongs.first().id
        }
    }

    LaunchedEffect(musicViewModel) {
        var lastSongId: String? = null
        var lastPosMs = 0L
        musicViewModel.positionMs.collect { posMs ->
            val activeId = musicViewModel.currentSong.value?.id
            if (activeId != null && activeId == lastSongId) {
                if (abs(posMs - lastPosMs) > 1_800L) {
                    externalReactionTrigger += 1
                }
            }
            lastSongId = activeId
            lastPosMs = posMs
        }
    }

    // Безопасная отправка трека в Комнату с учётом настроек «Только по Wi-Fi» и «Только безопасные MP3»
    val shareSongWithGuards: (Song) -> Unit = shareGuard@{ song ->
        if (appSettings.wifiOnlyTransfers && !AppSettingsStore.isWifiConnected(appContext)) {
            extendedRoom.toastNotice = "Включена передача только по Wi-Fi (подключитесь к Wi-Fi)"
            return@shareGuard
        }
        if (appSettings.onlySafeMp3Files) {
            val path = song.uri.path
            val localFile = if (!path.isNullOrBlank()) File(path) else null
            val ok = if (localFile != null && localFile.exists()) {
                AppSettingsStore.isSafeMp3File(localFile)
            } else {
                AppSettingsStore.isSafeMp3Uri(appContext, song.uri)
            }
            if (!ok) {
                extendedRoom.toastNotice = "Отправка отклонена: разрешены только безопасные MP3"
                return@shareGuard
            }
        }
        connectionManager.shareSong(song)
    }

    // Системный выбор песен через "+" с проверкой на безопасный MP3 (если включено) и защитой от дубликатов
    val musicPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        val shareToRoom = pendingShareAfterPick
        pendingShareAfterPick = false

        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                val (mp3FilteredUris, rejectedMp3Count) = if (appSettings.onlySafeMp3Files) {
                    withContext(Dispatchers.IO) {
                        AppSettingsStore.filterSafeMp3PickerUris(appContext, uris)
                    }
                } else {
                    uris to 0
                }

                val currentLib = musicViewModel.songs.value.filter {
                    !it.isRemote || it.id in FavoriteSongsStore.loadSavedRemoteIds(appContext).toSet()
                }
                val (uniqueUris, skippedDuplicates) = withContext(Dispatchers.IO) {
                    SongDuplicateGuard.filterNonDuplicatePickerUris(
                        context = appContext,
                        candidateUris = mp3FilteredUris,
                        existingLibrarySongs = currentLib
                    )
                }

                val idsBefore = musicViewModel.songs.value.map { it.id }.toSet()
                if (uniqueUris.isNotEmpty()) {
                    musicViewModel.addSongs(uniqueUris)
                }

                // Если "+" был нажат прямо в Комнате и у пользователя есть права — сразу отправляем добавленные песни в комнату
                if (
                    shareToRoom &&
                    uniqueUris.isNotEmpty() &&
                    connectionManager.currentRoomCode != null &&
                    connectionManager.canShareMusic() &&
                    !connectionManager.isBusy
                ) {
                    val newlyAdded = musicViewModel.songs.value.filter { it.id !in idsBefore }
                    if (newlyAdded.isNotEmpty()) {
                        if (appSettings.wifiOnlyTransfers && !AppSettingsStore.isWifiConnected(appContext)) {
                            extendedRoom.toastNotice =
                                "Песни добавлены локально. Для отправки в комнату нужен Wi-Fi"
                        } else {
                            connectionManager.shareSongs(newlyAdded)
                        }
                    }
                }

                when {
                    rejectedMp3Count > 0 && uniqueUris.isEmpty() -> {
                        globalBannerNotice = "Разрешены только безопасные MP3-файлы (отклонено: $rejectedMp3Count)"
                    }
                    skippedDuplicates > 0 && uniqueUris.isEmpty() -> {
                        globalBannerNotice = "Эта песня уже добавлена в вашу библиотеку"
                    }
                    rejectedMp3Count > 0 || skippedDuplicates > 0 -> {
                        globalBannerNotice = buildString {
                            append("Добавлено: ${uniqueUris.size}")
                            if (skippedDuplicates > 0) append(" • дубликатов: $skippedDuplicates")
                            if (rejectedMp3Count > 0) append(" • не MP3: $rejectedMp3Count")
                        }
                    }
                }
            }
        }
    }

    val launchSongPicker: (forRoom: Boolean) -> Unit = { forRoom ->
        pendingShareAfterPick = forRoom
        val mimeFilter = if (appSettings.onlySafeMp3Files) "audio/mpeg" else "audio/*"
        musicPickerLauncher.launch(mimeFilter)
    }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val updated = UserProfileStore.importAvatarFromUri(appContext, uri)
                if (updated != null) {
                    localProfile = updated
                    globalBannerNotice = "Аватарка профиля обновлена"
                }
            }
        }
    }

    // Сохранение трека из Комнаты в личную библиотеку пользователя (с защитой от дубликатов, проверкой Wi-Fi и MP3)
    val saveRoomTrackToPersonalLibrary: (RoomTrack?, Song?) -> Unit = saveGuard@{ roomTrack, songObj ->
        val candidateId = roomTrack?.id ?: songObj?.id.orEmpty()
        val candidateTitle = roomTrack?.title ?: songObj?.title.orEmpty()

        val alreadyExists = SongDuplicateGuard.isTrackAlreadyInLibrary(
            trackId = candidateId,
            trackTitle = candidateTitle,
            librarySongs = userLibrarySongs
        )
        if (alreadyExists) {
            extendedRoom.toastNotice = "Эта песня уже есть в вашей библиотеке"
            return@saveGuard
        }

        if (songObj != null && allSongs.any { it.id == songObj.id }) {
            if (appSettings.onlySafeMp3Files) {
                val f = songObj.uri.path?.let { File(it) }
                if (f != null && f.exists() && !AppSettingsStore.isSafeMp3File(f)) {
                    extendedRoom.toastNotice = "Сохранение отклонено: включён фильтр «Только безопасные MP3»"
                    return@saveGuard
                }
            }
            markRemoteSongSavedToLibrary(songObj.id)
            extendedRoom.toastNotice = "Песня добавлена в вашу библиотеку"
        } else if (roomTrack != null) {
            val cachedSong = allSongs.firstOrNull {
                it.id == roomTrack.id ||
                    SongDuplicateGuard.normalizeSongTitle(it.title) ==
                    SongDuplicateGuard.normalizeSongTitle(roomTrack.title)
            }
            if (cachedSong != null) {
                if (appSettings.onlySafeMp3Files) {
                    val f = cachedSong.uri.path?.let { File(it) }
                    if (f != null && f.exists() && !AppSettingsStore.isSafeMp3File(f)) {
                        extendedRoom.toastNotice = "Сохранение отклонено: файл не является безопасным MP3"
                        return@saveGuard
                    }
                }
                markRemoteSongSavedToLibrary(cachedSong.id)
                extendedRoom.toastNotice = "Песня добавлена в вашу библиотеку"
            } else {
                if (appSettings.wifiOnlyTransfers && !AppSettingsStore.isWifiConnected(appContext)) {
                    extendedRoom.toastNotice = "Включена загрузка только по Wi-Fi"
                    return@saveGuard
                }
                if (appSettings.onlySafeMp3Files && !AppSettingsStore.isSafeMp3RoomTrackMetadata(roomTrack)) {
                    extendedRoom.toastNotice = "Скачивание отклонено: разрешены только безопасные MP3"
                    return@saveGuard
                }
                connectionManager.downloadTrack(roomTrack) { downloadedFile ->
                    if (appSettings.onlySafeMp3Files && !AppSettingsStore.isSafeMp3File(downloadedFile)) {
                        runCatching { downloadedFile.delete() }
                        extendedRoom.toastNotice = "Файл отклонён проверкой безопасности MP3"
                        return@downloadTrack
                    }
                    musicViewModel.addSongFromFile(
                        file = downloadedFile,
                        title = roomTrack.title,
                        songId = roomTrack.id
                    )
                    markRemoteSongSavedToLibrary(roomTrack.id)
                    extendedRoom.toastNotice = "Песня скачана в вашу библиотеку"
                }
            }
        }
    }

    val deleteSongEverywhere: (Song) -> Unit = { songToDelete ->
        musicViewModel.removeSong(songToDelete)
        if (songToDelete.id in favoriteSongIds) {
            val updated = favoriteSongIds.filterNot { it == songToDelete.id }
            favoriteSongIds = updated
            FavoriteSongsStore.saveFavoriteIds(appContext, updated)
        }
        if (songToDelete.id in savedRemoteSongIds) {
            val updatedSaved = savedRemoteSongIds.filterNot { it == songToDelete.id }
            savedRemoteSongIds = updatedSaved
            FavoriteSongsStore.saveSavedRemoteIds(appContext, updatedSaved)
        }
    }

    SharyTheme(themeMode = appSettings.themeMode) {
        SharyShellScreen(
            selectedTab = selectedTab,
            onTabSelect = { tab ->
                if (tab == SharyTab.LIBRARY) {
                    if (selectedTab == SharyTab.LIBRARY) {
                        if (isLibraryPlayerOpen) {
                            isLibraryPlayerOpen = false
                        } else if (isSearchActive) {
                            closeSearchCleanly()
                        }
                    } else {
                        selectedTab = SharyTab.LIBRARY
                        isLibraryPlayerOpen = false
                    }
                } else {
                    selectedTab = tab
                }
            },
            onAddSongsClick = { launchSongPicker(false) },
            songs = userLibrarySongs,
            currentSongId = currentSong?.id,
            playerSelectedSongId = playerSelectedSongId,
            isLibraryPlayerOpen = isLibraryPlayerOpen,
            favoriteSongIds = favoriteSongIdSet,
            isSearchActive = isSearchActive,
            searchQuery = searchQuery,
            onSearchQueryChange = { newQuery -> searchQuery = newQuery },
            recentSearches = recentSearches,
            onSelectRecentSearch = { chosen ->
                searchQuery = chosen
                recordRecent(chosen)
            },
            onRemoveRecentSearch = { itemToRemove ->
                val updated = recentSearches.filterNot { it == itemToRemove }
                recentSearches = updated
                FavoriteSongsStore.saveRecentSearches(appContext, updated)
            },
            onClearRecentSearches = {
                recentSearches = emptyList()
                FavoriteSongsStore.saveRecentSearches(appContext, emptyList())
            },
            onCommitSearchEntry = { entry -> recordRecent(entry) },
            onCloseSearch = closeSearchCleanly,
            onCollapseLibraryPlayer = {
                isLibraryPlayerOpen = false
            },
            onSelectPlayerSongOnly = { browsedSong ->
                playerSelectedSongId = browsedSong.id
            },
            onPlaySpecificSong = { chosenSong ->
                if (activeRoomCode != null && !connectionManager.canControlPlayback()) {
                    globalBannerNotice = "В комнате управлять воспроизведением могут только админы"
                } else {
                    playerSelectedSongId = chosenSong.id
                    if (currentSong?.id != chosenSong.id) {
                        musicViewModel.playSong(chosenSong)
                    } else if (!isPlaying) {
                        musicViewModel.toggleMusic()
                    }
                }
            },
            onPlayPauseSelectedSong = { selectedSong ->
                if (activeRoomCode != null && !connectionManager.canControlPlayback()) {
                    globalBannerNotice = "В комнате управлять воспроизведением могут только админы"
                } else {
                    playerSelectedSongId = selectedSong.id
                    if (currentSong?.id == selectedSong.id) {
                        musicViewModel.toggleMusic()
                    } else {
                        musicViewModel.playSong(selectedSong)
                    }
                }
            },
            onSeekSelectedSong = { song, targetMs ->
                if (activeRoomCode != null && !connectionManager.canControlPlayback()) {
                    globalBannerNotice = "В комнате перематывать могут только админы"
                } else {
                    if (currentSong?.id != song.id) {
                        val wasPlaying = isPlaying
                        playerSelectedSongId = song.id
                        musicViewModel.playSong(song)
                        if (!wasPlaying) {
                            musicViewModel.toggleMusic()
                        }
                    }
                    musicViewModel.seekTo(targetMs)
                }
            },
            positionMsFlow = musicViewModel.positionMs,
            durationMsFlow = musicViewModel.durationMs,
            onTopLogoClick = {
                if (isLibraryPlayerOpen) {
                    isLibraryPlayerOpen = false
                } else if (isSearchActive) {
                    closeSearchCleanly()
                }
            },
            onTopSearchClick = {
                if (selectedTab != SharyTab.LIBRARY) {
                    selectedTab = SharyTab.LIBRARY
                    isLibraryPlayerOpen = false
                    isSearchActive = true
                } else if (isLibraryPlayerOpen) {
                    isLibraryPlayerOpen = false
                    isSearchActive = true
                } else if (isSearchActive) {
                    closeSearchCleanly()
                } else {
                    isSearchActive = true
                }
            },
            activeRoomCode = activeRoomCode,
            onExitRoomClick = {
                showLeaveRoomConfirm = true
            },
            onCopyRoomCodeClick = {
                val code = activeRoomCode ?: return@SharyShellScreen
                val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("SHARY Room Code", code))
                extendedRoom.toastNotice = "Код комнаты #$code скопирован"
            },
            onShareRoomCodeClick = {
                val code = activeRoomCode ?: return@SharyShellScreen
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Присоединяйся к комнате прослушивания в SHARY! Код комнаты: $code"
                    )
                }
                val chooser = Intent.createChooser(shareIntent, "Поделиться кодом комнаты").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { activityContext.startActivity(chooser) }
            },
            globalBannerNotice = globalBannerNotice,
            onDismissBannerNotice = { globalBannerNotice = null },
            isPlaying = isPlaying,
            isShuffleEnabled = isShuffleEnabled,
            isRepeatEnabled = isRepeatEnabled,
            vibeMotionMultiplier = appSettings.vibeMotionLevel.multiplier,
            externalReactionTrigger = externalReactionTrigger,
            onSongClick = { clickedSong ->
                playerSelectedSongId = clickedSong.id
                isLibraryPlayerOpen = true
                if (activeRoomCode == null || connectionManager.canControlPlayback()) {
                    if (currentSong?.id != clickedSong.id) {
                        musicViewModel.playSong(clickedSong)
                        musicViewModel.toggleMusic()
                    } else if (isPlaying) {
                        musicViewModel.toggleMusic()
                    }
                }
            },
            onDeleteSong = deleteSongEverywhere,
            onPlayPauseClick = {
                if (selectedTab == SharyTab.ROOM && activeRoomCode != null) {
                    if (!connectionManager.canControlPlayback()) {
                        extendedRoom.toastNotice = "Управлять воспроизведением могут только админы"
                        return@SharyShellScreen
                    }
                    val roomPlaylist = connectionManager.roomTracks.mapNotNull { rt ->
                        allSongs.firstOrNull {
                            it.id == rt.id ||
                                SongDuplicateGuard.normalizeSongTitle(it.title) ==
                                SongDuplicateGuard.normalizeSongTitle(rt.title)
                        }
                    }
                    if (currentSong != null) {
                        musicViewModel.toggleMusic()
                    } else if (roomPlaylist.isNotEmpty()) {
                        musicViewModel.playSong(roomPlaylist.first())
                    } else if (userLibrarySongs.isNotEmpty() && connectionManager.canShareMusic()) {
                        extendedRoom.toastNotice = "Выберите мини-пластинку справа для отправки в комнату"
                    }
                } else {
                    if (currentSong != null) {
                        musicViewModel.toggleMusic()
                    } else if (userLibrarySongs.isNotEmpty()) {
                        musicViewModel.playSong(userLibrarySongs.first())
                    }
                }
            },
            onPreviousClick = {
                if (selectedTab == SharyTab.ROOM && activeRoomCode != null) {
                    if (!connectionManager.canControlPlayback()) {
                        extendedRoom.toastNotice = "Переключать треки могут только админы"
                        return@SharyShellScreen
                    }
                    val roomPlaylist = connectionManager.roomTracks.mapNotNull { rt ->
                        allSongs.firstOrNull {
                            it.id == rt.id ||
                                SongDuplicateGuard.normalizeSongTitle(it.title) ==
                                SongDuplicateGuard.normalizeSongTitle(rt.title)
                        }
                    }
                    if (roomPlaylist.isNotEmpty()) {
                        val pos = musicViewModel.positionMs.value
                        if (currentSong != null && pos > 3_000L) {
                            musicViewModel.seekTo(0L)
                        } else {
                            val idx = roomPlaylist.indexOfFirst { it.id == currentSong?.id }
                            val prevIdx = if (idx <= 0) roomPlaylist.lastIndex else idx - 1
                            musicViewModel.playSong(roomPlaylist[prevIdx])
                        }
                    }
                } else if (userLibrarySongs.isNotEmpty()) {
                    val anchorId = if (selectedTab == SharyTab.LIBRARY && isLibraryPlayerOpen) {
                        playerSelectedSongId ?: currentSong?.id
                    } else {
                        currentSong?.id
                    }
                    val currentPosMs = musicViewModel.positionMs.value
                    if (anchorId == currentSong?.id && currentPosMs > 3_000L && currentSong != null) {
                        musicViewModel.seekTo(0L)
                    } else {
                        val currentIdx = userLibrarySongs.indexOfFirst { it.id == anchorId }
                        val prevIdx = if (currentIdx <= 0) userLibrarySongs.lastIndex else currentIdx - 1
                        val prevSong = userLibrarySongs[prevIdx]
                        playerSelectedSongId = prevSong.id
                        musicViewModel.playSong(prevSong)
                    }
                }
            },
            onNextClick = {
                if (selectedTab == SharyTab.ROOM && activeRoomCode != null) {
                    if (!connectionManager.canControlPlayback()) {
                        extendedRoom.toastNotice = "Переключать треки могут только админы"
                        return@SharyShellScreen
                    }
                    val roomPlaylist = connectionManager.roomTracks.mapNotNull { rt ->
                        allSongs.firstOrNull {
                            it.id == rt.id ||
                                SongDuplicateGuard.normalizeSongTitle(it.title) ==
                                SongDuplicateGuard.normalizeSongTitle(rt.title)
                        }
                    }
                    if (roomPlaylist.isNotEmpty()) {
                        val anchorId = currentSong?.id
                        val currentIdx = roomPlaylist.indexOfFirst { it.id == anchorId }
                        val nextSong = when {
                            isShuffleEnabled && roomPlaylist.size > 1 -> {
                                roomPlaylist.filter { it.id != anchorId }.random()
                            }
                            else -> {
                                val nextIdx = if (currentIdx < 0) 0 else (currentIdx + 1) % roomPlaylist.size
                                roomPlaylist[nextIdx]
                            }
                        }
                        musicViewModel.playSong(nextSong)
                    }
                } else if (userLibrarySongs.isNotEmpty()) {
                    val anchorId = if (selectedTab == SharyTab.LIBRARY && isLibraryPlayerOpen) {
                        playerSelectedSongId ?: currentSong?.id
                    } else {
                        currentSong?.id
                    }
                    val currentIdx = userLibrarySongs.indexOfFirst { it.id == anchorId }
                    val nextSong = when {
                        isShuffleEnabled && userLibrarySongs.size > 1 -> {
                            userLibrarySongs.filter { it.id != anchorId }.random()
                        }
                        isRepeatEnabled && anchorId != null && userLibrarySongs.size == 1 -> {
                            if (currentSong?.id == anchorId) {
                                musicViewModel.seekTo(0L)
                                if (!isPlaying) musicViewModel.toggleMusic()
                                null
                            } else {
                                userLibrarySongs.first()
                            }
                        }
                        else -> {
                            val nextIdx = if (currentIdx < 0) 0 else (currentIdx + 1) % userLibrarySongs.size
                            userLibrarySongs[nextIdx]
                        }
                    }
                    if (nextSong != null) {
                        playerSelectedSongId = nextSong.id
                        musicViewModel.playSong(nextSong)
                    }
                }
            },
            onShuffleClick = {
                if (selectedTab == SharyTab.ROOM && activeRoomCode != null && !connectionManager.canControlPlayback()) {
                    extendedRoom.toastNotice = "Режимом воспроизведения управляют админы"
                } else {
                    isShuffleEnabled = !isShuffleEnabled
                }
            },
            onRepeatClick = {
                if (selectedTab == SharyTab.ROOM && activeRoomCode != null && !connectionManager.canControlPlayback()) {
                    extendedRoom.toastNotice = "Режимом воспроизведения управляют админы"
                } else {
                    isRepeatEnabled = !isRepeatEnabled
                }
            },
            onSeekToMs = { targetMs ->
                externalReactionTrigger += 1
                musicViewModel.seekTo(targetMs)
            },
            onToggleFavoriteSong = { songId ->
                val wasFavorite = songId in favoriteSongIds
                val updated = if (wasFavorite) {
                    favoriteSongIds.filterNot { it == songId }
                } else {
                    favoriteSongIds + songId
                }
                favoriteSongIds = updated
                FavoriteSongsStore.saveFavoriteIds(appContext, updated)
            },
            roomParticipantsStrip = {
                SharyRoomParticipantsStrip(
                    extendedRoom = extendedRoom,
                    localProfile = localProfile,
                    onParticipantAvatarClick = { device ->
                        val myId = extendedRoom.myUid ?: "local_self"
                        val isMe = device.id == myId || device.id == "local_self"
                        when {
                            isMe && connectionManager.myRole == RoomRole.ADMIN -> {
                                showAdminSelfSettings = true
                            }
                            isMe -> {
                                selectedTab = SharyTab.PROFILE
                            }
                            connectionManager.myRole == RoomRole.ADMIN || extendedRoom.canKickParticipant(device) -> {
                                managedParticipant = device
                            }
                            else -> {
                                val roleDesc = when (device.role) {
                                    RoomRole.ADMIN -> "Главный админ"
                                    RoomRole.MODERATOR -> "Подадмин"
                                    RoomRole.MEMBER -> "Участник"
                                }
                                extendedRoom.toastNotice =
                                    "${extendedRoom.resolveDisplayName(device)} • $roleDesc"
                            }
                        }
                    },
                    onParticipantKickBadgeClick = { device ->
                        if (extendedRoom.canKickParticipant(device)) {
                            kickConfirmParticipant = device
                        } else {
                            extendedRoom.toastNotice =
                                "Удалять участников могут только админы"
                        }
                    }
                )
            },
            roomMiddleContent = { triggerVibe ->
                SharyRoomMiddleContent(
                    connectionManager = connectionManager,
                    extendedRoom = extendedRoom,
                    allLoadedSongs = allSongs,
                    userLibrarySongs = userLibrarySongs,
                    currentSongId = currentSong?.id,
                    favoriteSongIds = favoriteSongIdSet,
                    isPlaying = isPlaying,
                    positionMsFlow = musicViewModel.positionMs,
                    durationMsFlow = musicViewModel.durationMs,
                    onPlayRoomSong = { song ->
                        triggerVibe()
                        if (currentSong?.id != song.id) {
                            musicViewModel.playSong(song)
                        } else if (!isPlaying) {
                            musicViewModel.toggleMusic()
                        }
                    },
                    onDownloadAndPlayRoomTrack = downloadAndPlay@{ rt ->
                        triggerVibe()
                        if (appSettings.wifiOnlyTransfers && !AppSettingsStore.isWifiConnected(appContext)) {
                            extendedRoom.toastNotice = "Включена загрузка только по Wi-Fi"
                            return@downloadAndPlay
                        }
                        if (appSettings.onlySafeMp3Files && !AppSettingsStore.isSafeMp3RoomTrackMetadata(rt)) {
                            extendedRoom.toastNotice = "Трек отклонён: разрешены только безопасные MP3"
                            return@downloadAndPlay
                        }
                        connectionManager.downloadTrack(rt) { file ->
                            if (appSettings.onlySafeMp3Files && !AppSettingsStore.isSafeMp3File(file)) {
                                runCatching { file.delete() }
                                extendedRoom.toastNotice = "Файл отклонён проверкой безопасности MP3"
                                return@downloadTrack
                            }
                            musicViewModel.addSongFromFile(
                                file = file,
                                title = rt.title,
                                songId = rt.id
                            )
                            val loaded = musicViewModel.songs.value.firstOrNull { it.id == rt.id }
                            if (loaded != null) {
                                musicViewModel.playSong(loaded)
                            }
                        }
                    },
                    onShareSongToRoom = { song ->
                        triggerVibe()
                        shareSongWithGuards(song)
                    },
                    onAddNewSongsToRoomClick = {
                        triggerVibe()
                        launchSongPicker(true)
                    },
                    onSeekRoomSong = { song, targetMs ->
                        if (currentSong?.id != song.id) {
                            val wasPlaying = isPlaying
                            musicViewModel.playSong(song)
                            if (!wasPlaying) {
                                musicViewModel.toggleMusic()
                            }
                        }
                        musicViewModel.seekTo(targetMs)
                    },
                    onSaveRoomTrackToLibrary = { rt, songObj ->
                        triggerVibe()
                        saveRoomTrackToPersonalLibrary(rt, songObj)
                    },
                    onToggleFavoriteSong = { favId ->
                        triggerVibe()
                        val wasFav = favId in favoriteSongIds
                        val updated = if (wasFav) favoriteSongIds.filterNot { it == favId } else favoriteSongIds + favId
                        favoriteSongIds = updated
                        FavoriteSongsStore.saveFavoriteIds(appContext, updated)
                    },
                    onOpenChatClick = {
                        triggerVibe()
                        extendedRoom.openChat()
                    }
                )
            },
            middleContent = { triggerVibe ->
                when (selectedTab) {
                    SharyTab.PROFILE -> {
                        SharyProfileContent(
                            profile = localProfile,
                            themeMode = appSettings.themeMode,
                            songs = userLibrarySongs,
                            currentSongId = currentSong?.id,
                            isPlaying = isPlaying,
                            onPickAvatarClick = { avatarPickerLauncher.launch("image/*") },
                            onClearAvatarClick = {
                                localProfile = UserProfileStore.clearAvatar(appContext)
                                globalBannerNotice = "Аватарка удалена"
                            },
                            onSaveDisplayName = { name ->
                                localProfile = UserProfileStore.saveDisplayName(appContext, name)
                                globalBannerNotice = "Ник профиля сохранён"
                            },
                            onSelectThemeMode = { chosenMode ->
                                triggerVibe()
                                updateSettings(appSettings.copy(themeMode = chosenMode))
                            },
                            onDeleteSong = { song ->
                                triggerVibe()
                                deleteSongEverywhere(song)
                            }
                        )
                    }

                    SharyTab.SETTINGS -> {
                        SharySettingsContent(
                            settings = appSettings,
                            isWifiCurrentlyConnected = isWifiConnectedNow,
                            sleepTimerRemainingSec = sleepTimerRemainingSec,
                            tempCacheSizeBytes = tempCacheSizeBytes,
                            onUpdateSettings = { next ->
                                isWifiConnectedNow = AppSettingsStore.isWifiConnected(appContext)
                                updateSettings(next)
                            },
                            onSelectSleepTimerMinutes = { mins ->
                                sleepTimerRemainingSec = mins * 60
                                globalBannerNotice = if (mins > 0) {
                                    "Таймер сна включён на $mins мин."
                                } else {
                                    "Таймер сна выключен"
                                }
                            },
                            onClearTempCacheClick = {
                                coroutineScope.launch {
                                    val freed = withContext(Dispatchers.IO) {
                                        AppSettingsStore.clearTempCache(appContext)
                                    }
                                    tempCacheSizeBytes = withContext(Dispatchers.IO) {
                                        AppSettingsStore.calculateTempCacheBytes(appContext)
                                    }
                                    val mb = freed.toDouble() / (1024.0 * 1024.0)
                                    globalBannerNotice = String.format(
                                        java.util.Locale.US,
                                        "Кэш очищен (освобождено %.2f МБ)",
                                        mb
                                    )
                                }
                            }
                        )
                    }

                    else -> {}
                }
            }
        )

        // 1. Окно чата комнаты
        if (extendedRoom.isChatOpen && activeRoomCode != null) {
            SharyRoomChatDialog(
                roomCode = activeRoomCode,
                extendedRoom = extendedRoom,
                localProfile = localProfile,
                onDismiss = { extendedRoom.closeChat() }
            )
        }

        // 2. Окно настроек комнаты при нажатии главного админа на себя
        if (showAdminSelfSettings && activeRoomCode != null) {
            SharyAdminSelfSettingsDialog(
                extendedRoom = extendedRoom,
                onRoomDeleted = {
                    cleanupTemporaryUnsavedRoomSongs()
                },
                onDismiss = { showAdminSelfSettings = false }
            )
        }

        // 3. Окно управления участником
        val targetDevice = managedParticipant
        if (targetDevice != null && activeRoomCode != null) {
            SharyManageParticipantDialog(
                targetDevice = targetDevice,
                targetAvatar = extendedRoom.participantProfiles[targetDevice.id]?.avatarBitmap,
                targetDisplayName = extendedRoom.resolveDisplayName(targetDevice),
                extendedRoom = extendedRoom,
                onDismiss = { managedParticipant = null }
            )
        }

        // 4. Быстрое подтверждение кика по нажатию на маленькую мусорку участника
        val kickDevice = kickConfirmParticipant
        if (kickDevice != null && activeRoomCode != null) {
            SharyKickParticipantConfirmDialog(
                targetDevice = kickDevice,
                targetAvatar = extendedRoom.participantProfiles[kickDevice.id]?.avatarBitmap,
                targetDisplayName = extendedRoom.resolveDisplayName(kickDevice),
                extendedRoom = extendedRoom,
                onDismiss = { kickConfirmParticipant = null }
            )
        }

        // 5. Окно подтверждения выхода из комнаты
        if (showLeaveRoomConfirm && activeRoomCode != null) {
            SharyLeaveRoomConfirmDialog(
                extendedRoom = extendedRoom,
                onConfirmedLeave = {
                    cleanupTemporaryUnsavedRoomSongs()
                },
                onDismiss = { showLeaveRoomConfirm = false }
            )
        }
    }
}
