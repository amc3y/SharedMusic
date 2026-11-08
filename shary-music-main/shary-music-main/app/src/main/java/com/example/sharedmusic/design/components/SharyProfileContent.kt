package com.example.sharedmusic.design.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sharedmusic.design.navigation.LocalUserProfile
import com.example.sharedmusic.design.theme.SharyDimens
import com.example.sharedmusic.design.theme.SharyThemeMode
import com.example.sharedmusic.design.theme.SharyThemeTokens
import com.example.sharedmusic.logic.model.Song
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

// Экран 4-й вкладки «Профиль»:
// - смена аватарки (фото или статичный серый логотип SHARY) и ника;
// - переключение темы оформления: Тёмная, Серая (как сейчас) и Светлая;
// - нижний арочный слайдер мини-пластинок (огибающий круг "+", как в режиме прослушивания)
//   с кружочком удаления (мусоркой) справа снизу внутри каждой пластинки
//   и карточкой полной информации об отцентрованной песне (вес, длительность, тип файла, дата, поток и др.).
@Composable
fun SharyProfileContent(
    profile: LocalUserProfile,
    themeMode: SharyThemeMode,
    songs: List<Song>,
    currentSongId: String?,
    isPlaying: Boolean,
    favoriteSongIds: Set<String>,
    onPickAvatarClick: () -> Unit,
    onClearAvatarClick: () -> Unit,
    onSaveDisplayName: (String) -> Unit,
    onSelectThemeMode: (SharyThemeMode) -> Unit,
    onDeleteSong: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    val surfaces = SharyThemeTokens.surfaces
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    var nameDraft by remember { mutableStateOf(profile.displayName) }

    LaunchedEffect(profile.displayName) {
        if (profile.displayName != nameDraft) {
            nameDraft = profile.displayName
        }
    }

    val metadataMap = rememberSongsMetadataMap(songs)

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { songs.size.coerceAtLeast(1) }
    )

    // Если после удаления песни индекс вышел за пределы, плавно корректируем страницу
    LaunchedEffect(songs.size) {
        if (songs.isNotEmpty() && pagerState.currentPage > songs.lastIndex) {
            pagerState.scrollToPage(songs.lastIndex)
        }
    }

    val centeredSong: Song? = if (songs.isNotEmpty()) {
        songs.getOrNull(pagerState.currentPage.coerceIn(0, songs.lastIndex))
    } else {
        null
    }
    val centeredMeta: SongExtraMetadata? = centeredSong?.let { metadataMap[it.id] }

    val cardShape = RoundedCornerShape(12.dp)
    val archLiftPx = with(density) { SharyDimens.PlayerMiniPlateArchLift.toPx() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ВЕРХНЯЯ ПРОКРУЧИВАЕМАЯ ОБЛАСТЬ ПРОФИЛЯ
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. БЛОК АВАТАРКИ И НИКА
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(surfaces.songRowFill)
                    .border(SharyDimens.GlassBorderWidth, surfaces.songRowBorder, cardShape)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Круглая аватарка: фото пользователя или статичный серый логотип SHARY
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, surfaces.textPrimary.copy(alpha = 0.28f), CircleShape)
                            .clickable(onClick = onPickAvatarClick),
                        contentAlignment = Alignment.Center
                    ) {
                        if (profile.avatarBitmap != null) {
                            Image(
                                bitmap = profile.avatarBitmap,
                                contentDescription = "Аватар профиля",
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

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = profile.displayName.ifBlank { "Профиль участника" },
                            color = surfaces.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MonochromeActionPill(
                                text = if (profile.avatarBitmap == null) "Загрузить фото" else "Сменить фото",
                                filled = true,
                                onClick = onPickAvatarClick
                            )
                            if (profile.avatarBitmap != null) {
                                MonochromeActionPill(
                                    text = "Сброс",
                                    filled = false,
                                    onClick = onClearAvatarClick
                                )
                            }
                        }
                    }
                }

                // Поле ввода и сохранения ника
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(surfaces.fieldFill)
                            .border(1.dp, surfaces.fieldBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (nameDraft.isEmpty()) {
                            Text(
                                text = "Введите ваш ник...",
                                color = surfaces.textSecondary,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                        BasicTextField(
                            value = nameDraft,
                            onValueChange = { nameDraft = it.take(24) },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = surfaces.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(surfaces.textPrimary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    onSaveDisplayName(nameDraft)
                                    focusManager.clearFocus()
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    MonochromeActionPill(
                        text = "Сохранить",
                        filled = true,
                        onClick = {
                            onSaveDisplayName(nameDraft)
                            focusManager.clearFocus()
                        }
                    )
                }
            }

            // 2. ВЫБОР ТЕМЫ ОФОРМЛЕНИЯ: ТЁМНАЯ / СЕРАЯ (КАК СЕЙЧАС) / СВЕТЛАЯ
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(surfaces.songRowFill)
                    .border(SharyDimens.GlassBorderWidth, surfaces.songRowBorder, cardShape)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Тема оформления",
                    color = surfaces.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SharyThemeMode.entries.forEach { mode ->
                        MonochromeActionPill(
                            text = mode.titleRu,
                            filled = themeMode == mode,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectThemeMode(mode) }
                        )
                    }
                }
            }

            // 3. ИНСПЕКТОР ОТЦЕНТРОВАННОЙ ПЕСНИ (показывает все свойства файла при пролистывании нижнего слайдера)
            if (centeredSong != null) {
                CenteredSongMetadataCard(
                    song = centeredSong,
                    metadata = centeredMeta,
                    songIndex = pagerState.currentPage + 1,
                    totalSongs = songs.size,
                    isFavorite = centeredSong.id in favoriteSongIds
                )
            }
        }

        // 4. НИЖНИЙ АРОЧНЫЙ СЛАЙДЕР МИНИ-ПЛАСТИНОК (огибает центральный круг "+", как в режиме прослушивания)
        if (songs.isNotEmpty()) {
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
                    val isCurrentlyPlaying = plateSong.id == currentSongId && isPlaying

                    MiniSongPlateCard(
                        title = plateSong.title,
                        coverBitmap = plateMeta?.coverBitmap,
                        isCentered = isCentered,
                        isPlayingPlate = isCurrentlyPlaying,
                        onClick = {
                            if (pagerState.currentPage != page) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(page)
                                }
                            }
                        },
                        onDeleteClick = if (isCentered) {
                            { onDeleteSong(plateSong) }
                        } else {
                            null
                        },
                        modifier = Modifier
                            .size(plateSize)
                            .graphicsLayer {
                                val dist = abs(
                                    (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                                ).coerceIn(0f, 1f)
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
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Добавьте песни кнопкой «+» снизу, чтобы просматривать их свойства",
                    color = surfaces.textSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun CenteredSongMetadataCard(
    song: Song,
    metadata: SongExtraMetadata?,
    songIndex: Int,
    totalSongs: Int,
    isFavorite: Boolean
) {
    val surfaces = SharyThemeTokens.surfaces
    val shape = RoundedCornerShape(12.dp)
    val dateFormatter = remember { SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.getDefault()) }

    val durationMs = metadata?.durationMs?.takeIf { it > 0L } ?: song.durationMs
    val sizeBytes = metadata?.sizeBytes ?: 0L
    val sizeText = when {
        sizeBytes >= 1024L * 1024L -> {
            val mb = sizeBytes.toDouble() / (1024.0 * 1024.0)
            String.format(Locale.US, "%.2f МБ", mb)
        }
        sizeBytes > 0L -> {
            val kb = sizeBytes.toDouble() / 1024.0
            String.format(Locale.US, "%.0f КБ", kb)
        }
        else -> "—"
    }

    val formatUpper = (metadata?.extension ?: "audio").uppercase(Locale.ROOT)
    val dateText = if ((metadata?.addedTimestampMs ?: 0L) > 0L) {
        dateFormatter.format(Date(metadata!!.addedTimestampMs))
    } else {
        "—"
    }

    val streamDetails = buildString {
        if ((metadata?.bitrateKbps ?: 0) > 0) {
            append("${metadata!!.bitrateKbps} кбит/с")
        }
        if ((metadata?.sampleRateHz ?: 0) > 0) {
            if (isNotEmpty()) append(" • ")
            val khz = metadata!!.sampleRateHz / 1000.0
            append(String.format(Locale.US, "%.1f кГц", khz))
        }
        if (isEmpty()) {
            append(if (song.isRemote) "Сохранено из комнаты" else "Локальный файл")
        }
    }

    val sourceLabel = buildString {
        if (!metadata?.artist.isNullOrBlank()) {
            append(metadata!!.artist)
            append(" • ")
        }
        append(if (song.isRemote) "Из комнаты" else "Устройство")
        if (isFavorite) append(" • ♥")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(surfaces.songRowFill)
            .border(SharyDimens.GlassBorderWidth, surfaces.songRowBorder, shape)
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = song.title,
                color = surfaces.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$songIndex / $totalSongs",
                color = surfaces.textSecondary,
                fontSize = 11.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetadataMiniCell(
                label = "Вес",
                value = sizeText,
                modifier = Modifier.weight(1f)
            )
            MetadataMiniCell(
                label = "Длительность",
                value = formatSongDuration(durationMs),
                modifier = Modifier.weight(1f)
            )
            MetadataMiniCell(
                label = "Формат",
                value = formatUpper,
                modifier = Modifier.weight(0.8f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetadataMiniCell(
                label = "Дата добавления",
                value = dateText,
                modifier = Modifier.weight(1f)
            )
            MetadataMiniCell(
                label = "Параметры",
                value = streamDetails,
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = sourceLabel,
            color = surfaces.textSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MetadataMiniCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val surfaces = SharyThemeTokens.surfaces
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(surfaces.fieldFill)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = surfaces.textSecondary,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            color = surfaces.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
