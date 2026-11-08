package com.example.sharedmusic.design.navigation

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.OpenableColumns
import com.example.sharedmusic.design.theme.SharyThemeMode
import com.example.sharedmusic.logic.model.RoomTrack
import java.io.File
import java.util.Locale

private const val SETTINGS_PREFS = "shary_app_settings"

enum class VibeMotionLevel(
    val storageKey: String,
    val labelRu: String,
    val multiplier: Float
) {
    OFF("off", "Выкл", 0f),
    CALM("calm", "Спокойная", 0.55f),
    NORMAL("normal", "Обычная", 1.0f),
    ENERGETIC("energetic", "Активная", 1.45f);

    companion object {
        fun fromKey(key: String?): VibeMotionLevel {
            return entries.firstOrNull { it.storageKey == key } ?: NORMAL
        }
    }
}

enum class EqPreset(
    val storageKey: String,
    val labelRu: String,
    val gainsDb: List<Float>
) {
    FLAT("flat", "Ровный", listOf(0f, 0f, 0f, 0f, 0f)),
    BASS("bass", "Бас", listOf(7.5f, 5.0f, 0f, -1.0f, 1.5f)),
    ROCK("rock", "Рок", listOf(5.5f, 3.0f, -2.0f, 3.5f, 5.5f)),
    POP("pop", "Поп", listOf(-1.0f, 3.0f, 5.0f, 3.0f, -0.5f)),
    VOCAL("vocal", "Вокал", listOf(-3.0f, 1.0f, 5.5f, 4.0f, 1.0f)),
    ELECTRO("electro", "Электро", listOf(6.5f, 3.0f, -1.5f, 2.5f, 6.5f)),
    CUSTOM("custom", "Свой", listOf(0f, 0f, 0f, 0f, 0f));

    companion object {
        fun fromKey(key: String?): EqPreset {
            return entries.firstOrNull { it.storageKey == key } ?: FLAT
        }
    }
}

val EQ_BAND_LABELS = listOf("60 Гц", "230 Гц", "910 Гц", "3.6 кГц", "14 кГц")

data class AppSettingsState(
    val themeMode: SharyThemeMode = SharyThemeMode.GRAY,
    val wifiOnlyTransfers: Boolean = false,
    val onlySafeMp3Files: Boolean = false,
    val equalizerEnabled: Boolean = false,
    val eqPreset: EqPreset = EqPreset.FLAT,
    val eqBandLevelsDb: List<Float> = listOf(0f, 0f, 0f, 0f, 0f),
    val bassBoostPercent: Int = 0,
    val playerVolumePercent: Int = 100,
    val playbackSpeed: Float = 1.0f,
    val keepScreenOnInRoom: Boolean = true,
    val vibeMotionLevel: VibeMotionLevel = VibeMotionLevel.NORMAL
)

object AppSettingsStore {

    fun load(context: Context): AppSettingsState {
        val prefs = context.applicationContext.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        val theme = SharyThemeMode.fromKey(prefs.getString("theme_mode", SharyThemeMode.GRAY.storageKey))
        val wifiOnly = prefs.getBoolean("wifi_only_transfers", false)
        val safeMp3 = prefs.getBoolean("only_safe_mp3", false)
        val eqEnabled = prefs.getBoolean("eq_enabled", false)
        val preset = EqPreset.fromKey(prefs.getString("eq_preset", EqPreset.FLAT.storageKey))

        val bandsRaw = prefs.getString("eq_bands", null)
        val parsedBands = if (!bandsRaw.isNullOrBlank()) {
            val parts = bandsRaw.split(',')
            if (parts.size == 5) {
                parts.map { (it.toFloatOrNull() ?: 0f).coerceIn(-15f, 15f) }
            } else {
                preset.gainsDb
            }
        } else {
            preset.gainsDb
        }

        val bass = prefs.getInt("bass_boost_percent", 0).coerceIn(0, 100)
        val volume = prefs.getInt("player_volume_percent", 100).coerceIn(0, 100)
        val speed = prefs.getFloat("playback_speed", 1.0f).coerceIn(0.5f, 2.0f)
        val keepScreenOn = prefs.getBoolean("keep_screen_on_room", true)
        val motion = VibeMotionLevel.fromKey(prefs.getString("vibe_motion_level", VibeMotionLevel.NORMAL.storageKey))

        return AppSettingsState(
            themeMode = theme,
            wifiOnlyTransfers = wifiOnly,
            onlySafeMp3Files = safeMp3,
            equalizerEnabled = eqEnabled,
            eqPreset = preset,
            eqBandLevelsDb = parsedBands,
            bassBoostPercent = bass,
            playerVolumePercent = volume,
            playbackSpeed = speed,
            keepScreenOnInRoom = keepScreenOn,
            vibeMotionLevel = motion
        )
    }

    fun save(context: Context, state: AppSettingsState) {
        val prefs = context.applicationContext.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        val bandsStr = state.eqBandLevelsDb.take(5).joinToString(",") {
            String.format(Locale.US, "%.1f", it.coerceIn(-15f, 15f))
        }
        prefs.edit()
            .putString("theme_mode", state.themeMode.storageKey)
            .putBoolean("wifi_only_transfers", state.wifiOnlyTransfers)
            .putBoolean("only_safe_mp3", state.onlySafeMp3Files)
            .putBoolean("eq_enabled", state.equalizerEnabled)
            .putString("eq_preset", state.eqPreset.storageKey)
            .putString("eq_bands", bandsStr)
            .putInt("bass_boost_percent", state.bassBoostPercent.coerceIn(0, 100))
            .putInt("player_volume_percent", state.playerVolumePercent.coerceIn(0, 100))
            .putFloat("playback_speed", state.playbackSpeed.coerceIn(0.5f, 2.0f))
            .putBoolean("keep_screen_on_room", state.keepScreenOnInRoom)
            .putString("vibe_motion_level", state.vibeMotionLevel.storageKey)
            .apply()
    }

    // Проверка активного подключения к Wi-Fi (или проводному Ethernet)
    fun isWifiConnected(context: Context): Boolean {
        return try {
            val cm = context.applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (_: Exception) {
            false
        }
    }

    // Проверка выбранных в системном пикере URI на безопасный формат MP3 (расширение/MIME + сигнатура заголовка файла)
    fun filterSafeMp3PickerUris(
        context: Context,
        uris: List<Uri>
    ): Pair<List<Uri>, Int> {
        if (uris.isEmpty()) return emptyList<Uri>() to 0
        val accepted = ArrayList<Uri>(uris.size)
        var rejected = 0

        for (uri in uris) {
            if (isSafeMp3Uri(context, uri)) {
                accepted.add(uri)
            } else {
                rejected += 1
            }
        }
        return accepted to rejected
    }

    fun isSafeMp3Uri(context: Context, uri: Uri): Boolean {
        return try {
            val displayName = queryDisplayName(context, uri)
                ?: uri.lastPathSegment?.substringAfterLast('/')
                .orEmpty()
            val ext = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val mime = runCatching { context.contentResolver.getType(uri)?.lowercase(Locale.ROOT) }
                .getOrNull()
                .orEmpty()

            val looksLikeMp3 = ext == "mp3" || mime == "audio/mpeg" || mime == "audio/mp3"
            if (!looksLikeMp3) return false

            val header = ByteArray(4096)
            val read = context.contentResolver.openInputStream(uri)?.use { it.read(header) } ?: -1
            if (read < 4) return false

            isSafeMp3Header(header, read)
        } catch (_: Exception) {
            false
        }
    }

    fun isSafeMp3File(file: File): Boolean {
        if (!file.exists() || !file.isFile || file.length() < 4L) return false
        return try {
            val header = ByteArray(4096)
            val read = file.inputStream().use { it.read(header) }
            if (read < 4) return false
            isSafeMp3Header(header, read)
        } catch (_: Exception) {
            false
        }
    }

    fun isSafeMp3RoomTrackMetadata(track: RoomTrack): Boolean {
        val ext = track.fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        // Если расширение явно известно и это не mp3 (и не технический .audio), отклоняем до скачивания
        return ext.isEmpty() || ext == "mp3" || ext == "audio"
    }

    private fun isSafeMp3Header(buf: ByteArray, length: Int): Boolean {
        if (length < 4) return false
        // Запрещаем опасные исполняемые/архивные сигнатуры (MZ/PE, ZIP/APK, ELF)
        if (buf[0] == 'M'.code.toByte() && buf[1] == 'Z'.code.toByte()) return false
        if (buf[0] == 'P'.code.toByte() && buf[1] == 'K'.code.toByte()) return false
        if (buf[0] == 0x7F.toByte() && buf[1] == 'E'.code.toByte() &&
            buf[2] == 'L'.code.toByte() && buf[3] == 'F'.code.toByte()
        ) return false

        // 1) Тег ID3v2 ("ID3")
        if (buf[0] == 'I'.code.toByte() && buf[1] == 'D'.code.toByte() && buf[2] == '3'.code.toByte()) {
            return true
        }

        // 2) Поиск валидного MPEG Audio Frame Sync (11 единичных бит: 0xFF и (b1 & 0xE0) == 0xE0) в первых 4 КБ
        val limit = (length - 1).coerceAtMost(4094)
        for (i in 0 until limit) {
            val b0 = buf[i].toInt() and 0xFF
            val b1 = buf[i + 1].toInt() and 0xFF
            if (b0 == 0xFF && (b1 and 0xE0) == 0xE0 && (b1 and 0x18) != 0x08 && (b1 and 0x06) != 0x00) {
                return true
            }
        }
        return false
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun calculateTempCacheBytes(context: Context): Long {
        val appCtx = context.applicationContext
        var total = computeDirSize(appCtx.cacheDir)
        val roomCacheDir = File(appCtx.filesDir, "room_tracks")
        if (roomCacheDir.exists()) {
            total += computeDirSize(roomCacheDir)
        }
        return total
    }

    fun clearTempCache(context: Context): Long {
        val before = calculateTempCacheBytes(context)
        val appCtx = context.applicationContext
        deleteChildrenSafely(appCtx.cacheDir)
        val roomCacheDir = File(appCtx.filesDir, "room_tracks")
        if (roomCacheDir.exists()) {
            deleteChildrenSafely(roomCacheDir)
        }
        val after = calculateTempCacheBytes(context)
        return (before - after).coerceAtLeast(0L)
    }

    private fun computeDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        var sum = 0L
        dir.listFiles()?.forEach { child ->
            sum += computeDirSize(child)
        }
        return sum
    }

    private fun deleteChildrenSafely(dir: File?) {
        if (dir == null || !dir.exists() || !dir.isDirectory) return
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                deleteChildrenSafely(child)
            }
            runCatching { child.delete() }
        }
    }
}
