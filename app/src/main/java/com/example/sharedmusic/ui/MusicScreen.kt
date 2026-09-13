package com.example.sharedmusic.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DiscFull
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sharedmusic.player.MusicPlayer
import com.example.sharedmusic.ui.components.RecordDisc
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Локальный трек на главном экране (персистентность в Room — T5, партнёр). */
private data class HomeTrack(
    val uri: Uri,
    val name: String,
)

/**
 * Главный экран «Home» (home concept):
 * шапка с бейджем режима, пластинка, трек, сик-бар, кнопки,
 * список локальных треков, нижняя навигация.
 *
 * Примечание: состояние пока внутри экрана (как было в T4).
 * Переезд в MusicViewModel — задача T5 (партнёр, вместе с Room).
 */
@Composable
fun MusicScreen() {
    val context = LocalContext.current
    val musicPlayer = remember { MusicPlayer(context) }

    // ── Состояние экрана ─────────────────────────────────────────
    val tracks = remember { mutableStateListOf<HomeTrack>() }
    var currentUri by remember { mutableStateOf<Uri?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    val snackbarHostState = remember { SnackbarHostState() }
    // Корутина экрана: через неё вызываем suspend-функции (showSnackbar)
    val scope = rememberCoroutineScope()

    fun selectAndPlay(uri: Uri) {
        currentUri = uri
        musicPlayer.play(uri)
    }

    // Добавляем файл в список и запускаем
    val musicPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            if (tracks.none { it.uri == uri }) {
                tracks.add(HomeTrack(uri, getTrackName(context, uri)))
            }
            selectAndPlay(uri)
        }
    }

    // Опрос позиции плеера (T5: перенести в общий PlaybackController)
    LaunchedEffect(Unit) {
        while (true) {
            positionMs = musicPlayer.positionMs()
            durationMs = musicPlayer.durationMs()
            isPlaying = musicPlayer.isPlayingNow()
            delay(250)
        }
    }

    DisposableEffect(Unit) {
        onDispose { musicPlayer.release() }
    }

    val currentTrack = tracks.firstOrNull { it.uri == currentUri }
    val currentIndex = tracks.indexOfFirst { it.uri == currentUri }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            BottomNavigation(
                onSoon = { message ->
                    // showSnackbar — suspend-функция, поэтому внутри корутины
                    scope.launch { snackbarHostState.showSnackbar(message) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Header(onAddClick = { musicPicker.launch("audio/*") })

            // ── Пластинка + трек + управление ─────────────────────
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RecordDisc(
                    isSpinning = isPlaying,
                    discSize = 220.dp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )

                Text(
                    text = currentTrack?.name ?: "Трек не выбран",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (currentTrack == null) "Нажми «Добавить музыку» и выбери трек"
                    else "Локальный файл • Локальный режим",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))

                SeekBar(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onSeek = { musicPlayer.seekTo(it) },
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            if (currentIndex > 0) selectAndPlay(tracks[currentIndex - 1].uri)
                        },
                        enabled = currentIndex > 0,
                    ) {
                        Icon(
                            Icons.Filled.FastRewind,
                            contentDescription = "Предыдущий трек",
                            modifier = Modifier.size(32.dp),
                        )
                    }

                    FilledIconButton(
                        onClick = {
                            val uri = currentUri ?: return@FilledIconButton
                            if (isPlaying) musicPlayer.pause() else musicPlayer.play(uri)
                        },
                        enabled = currentUri != null,
                        modifier = Modifier.size(76.dp),
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Пауза" else "Играть",
                            modifier = Modifier.size(40.dp),
                        )
                    }

                    IconButton(
                        onClick = {
                            if (currentIndex < tracks.lastIndex) selectAndPlay(tracks[currentIndex + 1].uri)
                        },
                        enabled = currentIndex in 0 until tracks.lastIndex,
                    ) {
                        Icon(
                            Icons.Filled.FastForward,
                            contentDescription = "Следующий трек",
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
            }

            // ── Локальная библиотека ──────────────────────────────
            Text(
                text = "Локальная библиотека (${tracks.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )

            if (tracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Пока нет треков.\n«Добавить музыку» добавит их в список и запустит.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(tracks, key = { it.uri.toString() }) { track ->
                        TrackRow(
                            name = track.name,
                            isCurrent = track.uri == currentUri,
                            isPlaying = isPlaying,
                            onClick = { selectAndPlay(track.uri) },
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────── Подкомпоненты ───────────────────────────

@Composable
private fun Header(onAddClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "SharedMusic",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        // Бейдж режима: пользователь всегда видит LOCAL или SHARED (правило §45)
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Локальный режим",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onAddClick) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Добавить музыку")
        }
    }
}

@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
) {
    // Пока палец на ползунке — показываем палец, а не реальную позицию
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    val hasDuration = durationMs > 0
    val progress = dragPosition
        ?: if (hasDuration) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = progress,
            onValueChange = { dragPosition = it },
            onValueChangeFinished = {
                dragPosition?.let { onSeek((it * durationMs).toLong()) }
                dragPosition = null
            },
            enabled = hasDuration,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (hasDuration) formatTime(durationMs) else "--:--",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrackRow(
    name: String,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    val icon = when {
        isCurrent && isPlaying -> Icons.Filled.Equalizer
        isCurrent -> Icons.Filled.Pause
        else -> Icons.Filled.MusicNote
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BottomNavigation(onSoon: (String) -> Unit) {
    var selected by remember { mutableStateOf(0) }

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        NavigationBarItem(
            selected = selected == 0,
            onClick = { selected = 0 },
            icon = { Icon(Icons.Filled.DiscFull, contentDescription = null) },
            label = { Text("Главная") },
        )
        NavigationBarItem(
            selected = false,
            onClick = { onSoon("Комната — в планах T7–T10 (партнёр)") },
            icon = { Icon(Icons.Filled.Groups, contentDescription = null) },
            label = { Text("Комната") },
        )
        NavigationBarItem(
            selected = false,
            onClick = { onSoon("Библиотека — T5 (партнёр: Room + персистентность)") },
            icon = { Icon(Icons.Filled.LibraryMusic, contentDescription = null) },
            label = { Text("Библиотека") },
        )
        NavigationBarItem(
            selected = false,
            onClick = { onSoon("Профиль — T11") },
            icon = { Icon(Icons.Filled.Person, contentDescription = null) },
            label = { Text("Профиль") },
        )
    }
}

// 72000 мс -> "1:12"
private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    return "${totalSec / 60}:${(totalSec % 60).toString().padStart(2, '0')}"
}

// Настоящее имя файла (song.mp3), а не страшный content://...
private fun getTrackName(context: Context, uri: Uri): String {
    if (uri.scheme != "content") return uri.lastPathSegment ?: "Трек"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            return cursor.getString(nameIndex)
        }
    }
    return uri.lastPathSegment ?: "Трек"
}