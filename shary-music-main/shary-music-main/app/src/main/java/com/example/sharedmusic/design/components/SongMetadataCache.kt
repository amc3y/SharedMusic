package com.example.sharedmusic.design.components

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import com.example.sharedmusic.logic.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

// Расширенные метаданные аудиофайла для карточки трека, сортировки, фильтрации по формату и инспектора в Профиле
data class SongExtraMetadata(
    val coverBitmap: ImageBitmap? = null,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val addedTimestampMs: Long = 0L,
    val extension: String = "mp3",
    val bitrateKbps: Int = 0,
    val sampleRateHz: Int = 0,
    val artist: String = "",
    val mimeType: String = ""
)

private object SongMetadataMemoryCache {
    private val cache = object : LruCache<String, SongExtraMetadata>(128) {}

    fun get(songId: String): SongExtraMetadata? = synchronized(cache) {
        cache.get(songId)
    }

    fun put(songId: String, value: SongExtraMetadata) = synchronized(cache) {
        cache.put(songId, value)
    }
}

// Фоновая предзагрузка метаданных всего списка песен для мгновенной сортировки и карточки в Профиле
@Composable
fun rememberSongsMetadataMap(songs: List<Song>): Map<String, SongExtraMetadata> {
    val isPreview = LocalInspectionMode.current
    if (isPreview) {
        return remember(songs) {
            songs.mapIndexed { idx, s ->
                s.id to SongExtraMetadata(
                    coverBitmap = null,
                    durationMs = if (s.durationMs > 0L) s.durationMs else (180_000L + idx * 25_000L),
                    sizeBytes = 4_200_000L + idx * 950_000L,
                    addedTimestampMs = 1_700_000_000_000L + idx * 60_000L,
                    extension = if (idx % 3 == 2) "flac" else "mp3",
                    bitrateKbps = 320,
                    sampleRateHz = 44100,
                    artist = "",
                    mimeType = "audio/mpeg"
                )
            }.toMap()
        }
    }

    val context = LocalContext.current.applicationContext
    val mapState = remember { mutableStateOf<Map<String, SongExtraMetadata>>(emptyMap()) }

    LaunchedEffect(songs) {
        if (songs.isEmpty()) {
            mapState.value = emptyMap()
            return@LaunchedEffect
        }
        val result = withContext(Dispatchers.IO) {
            val loadedMap = LinkedHashMap<String, SongExtraMetadata>(songs.size)
            for (song in songs) {
                val cached = SongMetadataMemoryCache.get(song.id)
                if (cached != null) {
                    loadedMap[song.id] = cached
                } else {
                    val extracted = extractSongMetadata(context, song)
                    SongMetadataMemoryCache.put(song.id, extracted)
                    loadedMap[song.id] = extracted
                }
            }
            loadedMap
        }
        mapState.value = result
    }

    return mapState.value
}

@Composable
fun rememberSongExtraMetadata(song: Song): State<SongExtraMetadata> {
    val isPreview = LocalInspectionMode.current
    if (isPreview) {
        return remember(song.id) {
            mutableStateOf(
                SongExtraMetadata(
                    coverBitmap = null,
                    durationMs = if (song.durationMs > 0L) song.durationMs else 205_000L,
                    sizeBytes = 4_823_450L,
                    addedTimestampMs = 1_700_000_000_000L,
                    extension = "mp3",
                    bitrateKbps = 320,
                    sampleRateHz = 44100
                )
            )
        }
    }

    val context = LocalContext.current.applicationContext
    val cached = SongMetadataMemoryCache.get(song.id)

    return produceState(
        initialValue = cached ?: SongExtraMetadata(durationMs = song.durationMs),
        key1 = song.id,
        key2 = song.uri
    ) {
        val existing = SongMetadataMemoryCache.get(song.id)
        if (existing != null) {
            value = existing
        } else {
            val loaded = withContext(Dispatchers.IO) {
                extractSongMetadata(context = context, song = song)
            }
            SongMetadataMemoryCache.put(song.id, loaded)
            value = loaded
        }
    }
}

private fun extractSongMetadata(context: Context, song: Song): SongExtraMetadata {
    val path = song.uri.path
    val localFile = if (!path.isNullOrBlank()) File(path) else null
    val sizeBytes = if (localFile != null && localFile.exists() && localFile.isFile) {
        localFile.length()
    } else {
        resolveContentSizeBytes(context, song.uri)
    }
    val addedTimestampMs = if (localFile != null && localFile.exists()) {
        localFile.lastModified()
    } else {
        0L
    }
    val rawExt = localFile?.extension?.lowercase().orEmpty()

    var durationMs = song.durationMs
    var coverImage: ImageBitmap? = null
    var bitrateKbps = 0
    var sampleRateHz = 0
    var artist = ""
    var mimeType = ""

    val retriever = MediaMetadataRetriever()
    try {
        if (localFile != null && localFile.exists()) {
            retriever.setDataSource(localFile.absolutePath)
        } else {
            retriever.setDataSource(context, song.uri)
        }

        val extractedDuration = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
        if (extractedDuration != null && extractedDuration > 0L) {
            durationMs = extractedDuration
        }

        val extractedBitrate = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            ?.toLongOrNull()
        if (extractedBitrate != null && extractedBitrate > 0L) {
            bitrateKbps = (extractedBitrate / 1000L).toInt()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val sr = runCatching {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()
            }.getOrNull()
            if (sr != null && sr > 0) {
                sampleRateHz = sr
            }
        }

        artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            ?.trim()
            .orEmpty()

        mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            ?.trim()
            ?.lowercase()
            .orEmpty()

        val pictureBytes = retriever.embeddedPicture
        if (pictureBytes != null && pictureBytes.isNotEmpty()) {
            coverImage = decodeSampledBitmap(pictureBytes, maxSidePx = 256)
        }
    } catch (_: Exception) {
    } finally {
        try {
            retriever.release()
        } catch (_: Exception) {
        }
    }

    val resolvedExt = when {
        rawExt.isNotBlank() && rawExt != "audio" -> rawExt
        mimeType.contains("mpeg") || mimeType.contains("mp3") -> "mp3"
        mimeType.contains("flac") -> "flac"
        mimeType.contains("mp4") || mimeType.contains("aac") || mimeType.contains("m4a") -> "m4a"
        mimeType.contains("ogg") || mimeType.contains("vorbis") || mimeType.contains("opus") -> "ogg"
        mimeType.contains("wav") -> "wav"
        localFile != null && hasMp3Signature(localFile) -> "mp3"
        else -> rawExt.ifBlank { "audio" }
    }

    return SongExtraMetadata(
        coverBitmap = coverImage,
        durationMs = durationMs,
        sizeBytes = sizeBytes,
        addedTimestampMs = addedTimestampMs,
        extension = resolvedExt,
        bitrateKbps = bitrateKbps,
        sampleRateHz = sampleRateHz,
        artist = artist,
        mimeType = mimeType
    )
}

private fun hasMp3Signature(file: File): Boolean {
    return try {
        val header = ByteArray(16)
        val read = file.inputStream().use { it.read(header) }
        if (read < 3) return false
        val isId3 = header[0] == 'I'.code.toByte() &&
            header[1] == 'D'.code.toByte() &&
            header[2] == '3'.code.toByte()
        val isMpegSync = (header[0].toInt() and 0xFF) == 0xFF &&
            ((header[1].toInt() and 0xE0) == 0xE0)
        isId3 || isMpegSync
    } catch (_: Exception) {
        false
    }
}

private fun resolveContentSizeBytes(context: Context, uri: Uri): Long {
    return try {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
            afd.length.coerceAtLeast(0L)
        } ?: 0L
    } catch (_: Exception) {
        0L
    }
}

private fun decodeSampledBitmap(bytes: ByteArray, maxSidePx: Int): ImageBitmap? {
    return try {
        val boundsOpts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
        val rawMax = max(boundsOpts.outWidth, boundsOpts.outHeight)
        var sampleSize = 1
        while (rawMax / (sampleSize * 2) >= maxSidePx) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)?.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}
