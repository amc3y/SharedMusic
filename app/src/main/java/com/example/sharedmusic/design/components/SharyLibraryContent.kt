package com.example.sharedmusic.design.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sharedmusic.R
import com.example.sharedmusic.design.navigation.MAX_RECENT_SEARCH_ENTRIES
import com.example.sharedmusic.design.theme.SharyDimens
import com.example.sharedmusic.design.theme.SharyThemeTokens
import com.example.sharedmusic.logic.model.Song
import java.util.Locale

private enum class LibraryFilterTab {
    ALL_SONGS,
    FAVORITES
}

private const val MAX_RECENT_VISIBLE_CHARS = 30

// Содержимое списка треков в 1-м окне (Библиотека):
// - при вводе текста в поиске слева от лупы появляются крестик очистки и вертикальная чёрточка;
// - недавние слова (до 12 шт., новые вытесняют старые) имеют ширину по размеру самого слова с крестиком и складываются «кирпичиками» построчно.
@Composable
fun SharyLibraryContent(
    songs: List<Song>,
    currentSongId: String?,
    favoriteSongIds: Set<String>,
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    recentSearches: List<String>,
    onSelectRecentSearch: (String) -> Unit,
    onRemoveRecentSearch: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
    onCommitSearchEntry: (String) -> Unit,
    onSongClick: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onDeleteSong: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    val surfaces = SharyThemeTokens.surfaces
    var activeFilter by rememberSaveable { mutableStateOf(LibraryFilterTab.ALL_SONGS) }
    var sortMode by rememberSaveable { mutableStateOf(SongSortMode.DEFAULT) }

    val metadataMap = rememberSongsMetadataMap(songs)

    val displayedSongs = remember(
        songs,
        favoriteSongIds,
        activeFilter,
        isSearchActive,
        searchQuery,
        sortMode,
        metadataMap
    ) {
        val baseList = if (isSearchActive) {
            val query = searchQuery.trim()
            if (query.isEmpty()) {
                songs
            } else {
                songs.filter { song -> containsCharsInOrder(title = song.title, query = query) }
            }
        } else {
            when (activeFilter) {
                LibraryFilterTab.ALL_SONGS -> songs
                LibraryFilterTab.FAVORITES -> songs.filter { it.id in favoriteSongIds }
            }
        }

        if (!isSearchActive && sortMode == SongSortMode.DEFAULT) {
            baseList
        } else {
            applySortAndFormatFilter(
                list = baseList,
                mode = if (isSearchActive) sortMode else SongSortMode.DEFAULT,
                metadataMap = metadataMap
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Crossfade(
            targetState = isSearchActive,
            animationSpec = tween(durationMillis = 140),
            label = "libraryHeaderMerge"
        ) { searchMode ->
            if (searchMode) {
                MergedSearchHeaderBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onSubmitQuery = {
                        if (searchQuery.isNotBlank()) {
                            onCommitSearchEntry(searchQuery)
                        }
                    },
                    sortMode = sortMode,
                    onCycleSortMode = { sortMode = sortMode.next() }
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SharyDimens.SubTabBarHeight),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LibrarySubTabButton(
                        label = stringResource(R.string.tab_all_songs),
                        isSelected = activeFilter == LibraryFilterTab.ALL_SONGS,
                        onClick = { activeFilter = LibraryFilterTab.ALL_SONGS },
                        modifier = Modifier.weight(1f)
                    ) { tint ->
                        SharyNoteDownloadedIcon(tint = tint)
                    }

                    LibrarySubTabButton(
                        label = stringResource(R.string.tab_favorite_songs),
                        isSelected = activeFilter == LibraryFilterTab.FAVORITES,
                        onClick = { activeFilter = LibraryFilterTab.FAVORITES },
                        modifier = Modifier.weight(1f)
                    ) { tint ->
                        SharyHeartIcon(
                            filled = activeFilter == LibraryFilterTab.FAVORITES,
                            tint = tint,
                            modifier = Modifier.size(SharyDimens.SubTabIconSize)
                        )
                    }
                }
            }
        }

        // Недавние поиски «кирпичиками» (до 12 слов, размер равен слову + крестик)
        if (isSearchActive && searchQuery.isEmpty() && recentSearches.isNotEmpty()) {
            RecentSearchesSection(
                entries = recentSearches.take(MAX_RECENT_SEARCH_ENTRIES),
                onEntryClick = onSelectRecentSearch,
                onRemoveEntry = onRemoveRecentSearch,
                onClearAll = onClearRecentSearches
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (displayedSongs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                SharyEmptyPlaylistEmblem(
                    primaryTint = surfaces.textSecondary,
                    accentTint = surfaces.navSelectionCircleSelected,
                    modifier = Modifier
                        .sizeIn(
                            maxWidth = SharyDimens.EmptyStateIconSize,
                            maxHeight = SharyDimens.EmptyStateIconSize
                        )
                        .fillMaxSize(0.72f)
                        .aspectRatio(1f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 2.dp, bottom = 28.dp)
            ) {
                items(
                    items = displayedSongs,
                    key = { song -> song.id }
                ) { song ->
                    SharySongRow(
                        song = song,
                        preloadedMetadata = metadataMap[song.id],
                        isCurrentSong = song.id == currentSongId,
                        isFavorite = song.id in favoriteSongIds,
                        onRowClick = {
                            if (isSearchActive) {
                                onCommitSearchEntry(
                                    if (searchQuery.isNotBlank()) searchQuery.trim() else song.title
                                )
                            }
                            onSongClick(song)
                        },
                        onFavoriteClick = { onToggleFavorite(song) },
                        onDeleteClick = { onDeleteSong(song) }
                    )
                }
            }
        }
    }
}

// Единая поисковая строка: при наборе текста слева от лупы появляются крестик очистки и вертикальная чёрточка
@Composable
private fun MergedSearchHeaderBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmitQuery: () -> Unit,
    sortMode: SongSortMode,
    onCycleSortMode: () -> Unit
) {
    val surfaces = SharyThemeTokens.surfaces
    val shape = RoundedCornerShape(SharyDimens.PanelInnerElementRadius)
    val focusManager = LocalFocusManager.current

    val clearInteraction = remember { MutableInteractionSource() }
    val isClearPressed by clearInteraction.collectIsPressedAsState()
    val clearScaleState = rememberSharyInteractiveScaleState(
        isSelected = false,
        isPressed = isClearPressed
    )

    val sortInteraction = remember { MutableInteractionSource() }
    val isSortPressed by sortInteraction.collectIsPressedAsState()
    val isSortActive = sortMode != SongSortMode.DEFAULT
    val sortScaleState = rememberSharyInteractiveScaleState(
        isSelected = isSortActive,
        isPressed = isSortPressed
    )
    val sortCircleColorState = animateColorAsState(
        targetValue = when {
            isSortActive -> surfaces.navSelectionCircleSelected
            isSortPressed -> surfaces.navSelectionCirclePressed
            else -> Color.Transparent
        },
        animationSpec = tween(120),
        label = "sortCircleColor"
    )
    val sortTint by animateColorAsState(
        targetValue = if (isSortActive || isSortPressed) {
            surfaces.navIconSelected
        } else {
            surfaces.navIconInactive
        },
        animationSpec = tween(120),
        label = "sortIconTint"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SharyDimens.SubTabBarHeight)
            .clip(shape)
            .background(surfaces.songRowFill)
            .border(
                width = SharyDimens.GlassBorderWidth,
                color = surfaces.songRowBorder,
                shape = shape
            )
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.search_placeholder),
                    color = surfaces.textSecondary,
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = surfaces.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal
                ),
                cursorBrush = SolidColor(surfaces.textPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        onSubmitQuery()
                        focusManager.clearFocus()
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // При вводе символов слева от значка поиска появляются крестик удаления текста и вертикальная чёрточка
        if (query.isNotEmpty()) {
            Spacer(modifier = Modifier.width(4.dp))

            val clearTextDesc = stringResource(R.string.action_clear_search_text)
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .semantics {
                        contentDescription = clearTextDesc
                        role = Role.Button
                    }
                    .clickable(
                        interactionSource = clearInteraction,
                        indication = null,
                        onClick = {
                            if (query.isNotBlank()) {
                                onSubmitQuery()
                            }
                            onQueryChange("")
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                SharySmallCrossIcon(
                    tint = surfaces.textSecondary,
                    modifier = Modifier
                        .size(15.dp)
                        .graphicsLayer {
                            val s = clearScaleState.value
                            scaleX = s
                            scaleY = s
                        }
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(surfaces.textSecondary.copy(alpha = 0.42f))
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Нереагирующий декоративный значок лупы
        SharySearchIcon(
            tint = surfaces.textSecondary,
            modifier = Modifier.size(19.dp)
        )

        Spacer(modifier = Modifier.width(6.dp))

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(20.dp)
                .background(surfaces.textSecondary.copy(alpha = 0.42f))
        )

        Spacer(modifier = Modifier.width(2.dp))

        val sortDescription = stringResource(sortMode.labelResId)
        Box(
            modifier = Modifier
                .size(38.dp)
                .semantics {
                    contentDescription = sortDescription
                    role = Role.Button
                    selected = isSortActive
                }
                .drawBehind {
                    drawCircle(
                        color = sortCircleColorState.value,
                        radius = size.minDimension / 2f
                    )
                }
                .clickable(
                    interactionSource = sortInteraction,
                    indication = null,
                    onClick = onCycleSortMode
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.graphicsLayer {
                    val s = sortScaleState.value
                    scaleX = s
                    scaleY = s
                },
                contentAlignment = Alignment.Center
            ) {
                SharySortModeIcon(
                    mode = sortMode,
                    tint = sortTint,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
}

// Секция недавних слов «кирпичиками» (FlowRow): каждый прямоугольник равен по ширине слову + крестику,
// слова укладываются сначала в одну линию, затем переносятся на следующую (лимит 12 слов)
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentSearchesSection(
    entries: List<String>,
    onEntryClick: (String) -> Unit,
    onRemoveEntry: (String) -> Unit,
    onClearAll: () -> Unit
) {
    val surfaces = SharyThemeTokens.surfaces

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            entries.forEach { entry ->
                RecentSearchBrickChip(
                    text = entry,
                    onClick = { onEntryClick(entry) },
                    onRemove = { onRemoveEntry(entry) }
                )
            }
        }

        val clearInteraction = remember { MutableInteractionSource() }
        val isClearPressed by clearInteraction.collectIsPressedAsState()
        val clearScaleState = rememberSharyInteractiveScaleState(
            isSelected = false,
            isPressed = isClearPressed
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.action_clear_recent),
                color = surfaces.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .graphicsLayer {
                        val s = clearScaleState.value
                        scaleX = s
                        scaleY = s
                    }
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(
                        interactionSource = clearInteraction,
                        indication = null,
                        onClick = onClearAll
                    )
                    .padding(horizontal = 12.dp, vertical = 3.dp)
            )
        }
    }
}

// Отдельный «кирпичик» недавнего слова: компактный скруглённый прямоугольник строго по ширине слова с крестиком на конце
@Composable
private fun RecentSearchBrickChip(
    text: String,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val surfaces = SharyThemeTokens.surfaces
    val chipShape = RoundedCornerShape(6.dp)
    val needsCharFade = text.length > MAX_RECENT_VISIBLE_CHARS
    val clippedText = if (needsCharFade) text.take(MAX_RECENT_VISIBLE_CHARS) else text

    val removeInteraction = remember { MutableInteractionSource() }
    val isRemovePressed by removeInteraction.collectIsPressedAsState()
    val removeScaleState = rememberSharyInteractiveScaleState(
        isSelected = false,
        isPressed = isRemovePressed
    )

    val textFadeModifier = if (needsCharFade) {
        Modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.horizontalGradient(
                        0.0f to Color.Black,
                        0.70f to Color.Black,
                        1.0f to Color.Transparent
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .height(27.dp)
            .clip(chipShape)
            .background(surfaces.recentChipFill)
            .border(
                width = SharyDimens.GlassBorderWidth,
                color = surfaces.songRowBorder,
                shape = chipShape
            )
            .clickable(onClick = onClick)
            .padding(start = 9.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = textFadeModifier,
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = clippedText,
                color = surfaces.textPrimary.copy(alpha = 0.88f),
                fontSize = 12.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }

        Spacer(modifier = Modifier.width(3.dp))

        Box(
            modifier = Modifier
                .size(20.dp)
                .semantics {
                    contentDescription = text
                    role = Role.Button
                }
                .clickable(
                    interactionSource = removeInteraction,
                    indication = null,
                    onClick = onRemove
                ),
            contentAlignment = Alignment.Center
        ) {
            SharySmallCrossIcon(
                tint = surfaces.textSecondary,
                modifier = Modifier
                    .size(11.dp)
                    .graphicsLayer {
                        val s = removeScaleState.value
                        scaleX = s
                        scaleY = s
                    }
            )
        }
    }
}

@Composable
private fun LibrarySubTabButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconContent: @Composable (tint: Color) -> Unit
) {
    val surfaces = SharyThemeTokens.surfaces
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scaleState = rememberSharyInteractiveScaleState(
        isSelected = isSelected,
        isPressed = isPressed
    )
    val tabShape = RoundedCornerShape(SharyDimens.PanelInnerElementRadius)

    val containerColorState = animateColorAsState(
        targetValue = when {
            isSelected -> surfaces.navSelectionCircleSelected
            isPressed -> surfaces.navSelectionCirclePressed
            else -> surfaces.songRowFill
        },
        animationSpec = tween(durationMillis = 120),
        label = "subTabFill"
    )

    val iconTint by animateColorAsState(
        targetValue = if (isSelected || isPressed) {
            surfaces.navIconSelected
        } else {
            surfaces.navIconInactive
        },
        animationSpec = tween(durationMillis = 120),
        label = "subTabIconTint"
    )

    Box(
        modifier = modifier
            .height(SharyDimens.SubTabBarHeight)
            .semantics {
                contentDescription = label
                role = Role.Tab
                selected = isSelected
            }
            .clip(tabShape)
            .drawBehind {
                drawRect(color = containerColorState.value)
            }
            .border(
                width = SharyDimens.GlassBorderWidth,
                color = surfaces.songRowBorder,
                shape = tabShape
            )
            .clickable(
                interactionSource = interactionSource,
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
            iconContent(iconTint)
        }
    }
}

@Composable
private fun SharySongRow(
    song: Song,
    preloadedMetadata: SongExtraMetadata?,
    isCurrentSong: Boolean,
    isFavorite: Boolean,
    onRowClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val surfaces = SharyThemeTokens.surfaces
    val metadata = preloadedMetadata ?: rememberSongExtraMetadata(song).value
    val rowShape = RoundedCornerShape(SharyDimens.PanelInnerElementRadius)
    val coverShape = RoundedCornerShape(SharyDimens.PanelInnerElementRadius)

    val rowBackgroundState = animateColorAsState(
        targetValue = if (isCurrentSong) {
            surfaces.songRowActiveFill
        } else {
            surfaces.songRowFill
        },
        animationSpec = tween(durationMillis = 140),
        label = "songRowBackground"
    )

    val durationText = formatSongDuration(metadata.durationMs)
    val sizeMb = metadata.sizeBytes.toDouble() / (1024.0 * 1024.0)
    val sizeText = stringResource(
        R.string.song_size_mb_format,
        sizeMb.coerceAtLeast(0.1)
    )
    val metaSubtitle = if (metadata.sizeBytes > 0L) {
        "$durationText  •  $sizeText"
    } else {
        durationText
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .drawBehind {
                drawRect(color = rowBackgroundState.value)
            }
            .border(
                width = SharyDimens.GlassBorderWidth,
                color = surfaces.songRowBorder,
                shape = rowShape
            )
            .clickable(onClick = onRowClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(SharyDimens.SongCoverSize)
                .clip(coverShape)
                .background(surfaces.placeholderCoverFill),
            contentAlignment = Alignment.Center
        ) {
            val cover = metadata.coverBitmap
            if (cover != null) {
                Image(
                    bitmap = cover,
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                SharyLogoMark(
                    primaryColor = surfaces.placeholderLogoPrimary,
                    secondaryColor = surfaces.placeholderLogoSecondary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = song.title,
                color = surfaces.textPrimary,
                fontSize = 14.sp,
                fontWeight = if (isCurrentSong) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = metaSubtitle,
                color = surfaces.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        val favoriteLabel = if (isFavorite) {
            stringResource(R.string.action_favorite_remove)
        } else {
            stringResource(R.string.action_favorite_add)
        }
        val heartTint by animateColorAsState(
            targetValue = if (isFavorite) surfaces.heartActive else surfaces.navIconInactive,
            animationSpec = tween(durationMillis = 120),
            label = "songHeartTint"
        )
        SongRowIconButton(
            label = favoriteLabel,
            isSelected = isFavorite,
            onClick = onFavoriteClick
        ) {
            SharyHeartIcon(
                filled = isFavorite,
                tint = heartTint,
                modifier = Modifier.size(SharyDimens.SongActionIconSize)
            )
        }

        SongRowIconButton(
            label = stringResource(R.string.action_delete_song),
            isSelected = false,
            onClick = onDeleteClick
        ) {
            SharyTrashIcon(
                tint = surfaces.navIconInactive,
                modifier = Modifier.size(SharyDimens.SongActionIconSize)
            )
        }
    }
}

@Composable
private fun SongRowIconButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    iconContent: @Composable () -> Unit
) {
    val surfaces = SharyThemeTokens.surfaces
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scaleState = rememberSharyInteractiveScaleState(
        isSelected = isSelected,
        isPressed = isPressed
    )

    val pressCircleColorState = animateColorAsState(
        targetValue = if (isPressed) {
            surfaces.navSelectionCirclePressed
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 100),
        label = "songRowActionCircle"
    )

    Box(
        modifier = Modifier
            .size(SharyDimens.SongActionTouchSize)
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .drawBehind {
                drawCircle(
                    color = pressCircleColorState.value,
                    radius = size.minDimension / 2f
                )
            }
            .clickable(
                interactionSource = interactionSource,
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

private fun applySortAndFormatFilter(
    list: List<Song>,
    mode: SongSortMode,
    metadataMap: Map<String, SongExtraMetadata>
): List<Song> {
    return when (mode) {
        SongSortMode.DEFAULT -> list
        SongSortMode.SIZE_DESC -> list.sortedByDescending { metadataMap[it.id]?.sizeBytes ?: 0L }
        SongSortMode.SIZE_ASC -> list.sortedBy { metadataMap[it.id]?.sizeBytes ?: 0L }
        SongSortMode.DATE_DESC -> list.sortedByDescending { metadataMap[it.id]?.addedTimestampMs ?: 0L }
        SongSortMode.DATE_ASC -> list.sortedBy { metadataMap[it.id]?.addedTimestampMs ?: 0L }
        SongSortMode.DURATION_DESC -> list.sortedByDescending { metadataMap[it.id]?.durationMs ?: it.durationMs }
        SongSortMode.DURATION_ASC -> list.sortedBy { metadataMap[it.id]?.durationMs ?: it.durationMs }
        SongSortMode.TITLE_ASC -> list.sortedBy { it.title.lowercase() }
        SongSortMode.TITLE_DESC -> list.sortedByDescending { it.title.lowercase() }
        SongSortMode.ONLY_MP3 -> list.filter {
            (metadataMap[it.id]?.extension ?: it.uri.path?.substringAfterLast('.', "")?.lowercase()) == "mp3"
        }
        SongSortMode.NON_MP3 -> list.filter {
            val ext = metadataMap[it.id]?.extension ?: it.uri.path?.substringAfterLast('.', "")?.lowercase()
            !ext.isNullOrBlank() && ext != "mp3"
        }
    }
}

private fun containsCharsInOrder(title: String, query: String): Boolean {
    if (query.isEmpty()) return true
    var queryIndex = 0
    for (ch in title) {
        if (ch.equals(query[queryIndex], ignoreCase = true)) {
            queryIndex++
            if (queryIndex == query.length) return true
        }
    }
    return false
}

internal fun formatSongDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "0:00"
    val totalSeconds = (durationMs / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
