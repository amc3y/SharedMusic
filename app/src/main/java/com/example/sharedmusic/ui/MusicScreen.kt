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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.sharedmusic.player.MusicPlayer
import com.example.sharedmusic.ui.theme.SharedMusicTheme
import kotlinx.coroutines.delay

// Экран: связывает плеер и интерфейс
@Composable
fun MusicScreen() {
    val context = LocalContext.current
    val musicPlayer = remember { MusicPlayer(context) }

    var selectedMusic by rememberSaveable { mutableStateOf<Uri?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }

    val musicPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> selectedMusic = uri }

    // Раз в полсекунды спрашиваем у плеера: что сейчас играет
    LaunchedEffect(Unit) {
        while (true) {
            positionMs = musicPlayer.positionMs()
            durationMs = musicPlayer.durationMs()
            isPlaying = musicPlayer.isPlayingNow()
            delay(500)
        }
    }

    DisposableEffect(Unit) {
        onDispose { musicPlayer.release() }
    }

    val trackName = remember(selectedMusic) {
        selectedMusic?.let { getTrackName(context, it) }
    }

    PlayerContent(
        trackName = trackName,
        isPlaying = isPlaying,
        positionMs = positionMs,
        durationMs = durationMs,
        onAddClick = { musicPicker.launch("audio/*") },
        onPlayPauseClick = {
            val uri = selectedMusic ?: return@PlayerContent
            if (isPlaying) musicPlayer.pause() else musicPlayer.play(uri)
        },
        onStopClick = { musicPlayer.stop() },
        onSeek = { musicPlayer.seekTo(it) }
    )
}

// Чистый интерфейс без логики (его же видим в Preview)
@Composable
private fun PlayerContent(
    trackName: String?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onAddClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onStopClick: () -> Unit,
    onSeek: (Long) -> Unit
) {
    // Пока палец на ползунке — показываем палец, а не реальную позицию
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    val hasDuration = durationMs > 0
    val progress = dragPosition
        ?: if (hasDuration) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Обложка-заглушка
        Box(
            modifier = Modifier
                .size(220.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = "Обложка",
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = trackName ?: "Трек не выбран",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (trackName == null) "Нажми «Добавить музыку»" else "Локальный файл",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        Slider(
            value = progress,
            onValueChange = { dragPosition = it },
            onValueChangeFinished = {
                dragPosition?.let { onSeek((it * durationMs).toLong()) }
                dragPosition = null
            },
            enabled = hasDuration,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(positionMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (hasDuration) formatTime(durationMs) else "--:--",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onStopClick, enabled = trackName != null) {
                Icon(
                    Icons.Filled.Stop,
                    contentDescription = "Стоп",
                    modifier = Modifier.size(32.dp)
                )
            }
            FilledIconButton(
                onClick = onPlayPauseClick,
                enabled = trackName != null,
                modifier = Modifier.size(76.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Пауза" else "Играть",
                    modifier = Modifier.size(40.dp)
                )
            }
            // Пустышка для симметрии, чтобы Play был по центру
            Spacer(Modifier.size(48.dp))
        }

        Spacer(Modifier.height(24.dp))

        OutlinedButton(onClick = onAddClick) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Добавить музыку")
        }
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

@Preview(showBackground = true, name = "Светлая тема")
@Composable
private fun PlayerContentPreview() {
    SharedMusicTheme {
        PlayerContent(
            trackName = "emerald-skies.mp3",
            isPlaying = true,
            positionMs = 72_000,
            durationMs = 225_000,
            onAddClick = {},
            onPlayPauseClick = {},
            onStopClick = {},
            onSeek = {}
        )
    }
}