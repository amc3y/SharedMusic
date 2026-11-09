package com.example.sharedmusic.design.navigation

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.sharedmusic.logic.model.Song
import org.json.JSONArray
import java.io.File
import java.security.MessageDigest
import java.util.Locale

private const val PREFS_NAME = "shary_ui_favorites"
private const val KEY_FAVORITE_IDS = "favorite_song_ids"
private const val KEY_SAVED_REMOTE_SONG_IDS = "saved_remote_song_ids"
private const val KEY_RECENT_SEARCHES = "recent_search_queries_json"

// Лимит недавних поисковых слов — максимум 12 (новые слова заменяют самые старые)
const val MAX_RECENT_SEARCH_ENTRIES = 12

object FavoriteSongsStore {

    fun loadFavoriteIds(context: Context): List<String> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getStringSet(KEY_FAVORITE_IDS, emptySet()) ?: emptySet()
        return stored.toList()
    }

    fun saveFavoriteIds(context: Context, ids: Collection<String>) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet(KEY_FAVORITE_IDS, ids.toSet())
            .apply()
    }

    // ID треков, скачанных пользователем в свою библиотеку через кнопку загрузки в Комнате
    fun loadSavedRemoteIds(context: Context): List<String> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getStringSet(KEY_SAVED_REMOTE_SONG_IDS, emptySet()) ?: emptySet()
        return stored.toList()
    }

    fun saveSavedRemoteIds(context: Context, ids: Collection<String>) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet(KEY_SAVED_REMOTE_SONG_IDS, ids.toSet())
            .apply()
    }

    fun loadRecentSearches(context: Context): List<String> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_RECENT_SEARCHES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val result = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) {
                val item = arr.optString(i).trim()
                if (item.isNotEmpty() && item !in result) {
                    result.add(item)
                    if (result.size >= MAX_RECENT_SEARCH_ENTRIES) break
                }
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveRecentSearches(context: Context, entries: List<String>) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        entries.take(MAX_RECENT_SEARCH_ENTRIES).forEach { arr.put(it) }
        prefs.edit()
            .putString(KEY_RECENT_SEARCHES, arr.toString())
            .apply()
    }

    fun recordRecentSearch(current: List<String>, rawEntry: String): List<String> {
        val trimmed = rawEntry.trim()
        if (trimmed.isEmpty()) return current
        val deduplicated = current.filterNot { it.equals(trimmed, ignoreCase = true) }
        return (listOf(trimmed) + deduplicated).take(MAX_RECENT_SEARCH_ENTRIES)
    }
}

// Защита от повторного добавления одной и той же песни (и через "+" в библиотеке, и через загрузку в комнате)
object SongDuplicateGuard {

    fun normalizeSongTitle(rawTitle: String): String {
        val withoutExt = stripCommonAudioExtension(rawTitle.substringAfterLast('/'))
        return withoutExt
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
    }

    fun isTrackAlreadyInLibrary(
        trackId: String,
        trackTitle: String,
        librarySongs: List<Song>
    ): Boolean {
        val normTarget = normalizeSongTitle(trackTitle)
        return librarySongs.any { song ->
            song.id == trackId ||
                (normTarget.isNotEmpty() && normalizeSongTitle(song.title) == normTarget)
        }
    }

    // Фильтрует выбранные в системном пикере URI, отбрасывая дубликаты по названию и содержимому файла
    fun filterNonDuplicatePickerUris(
        context: Context,
        candidateUris: List<Uri>,
        existingLibrarySongs: List<Song>
    ): Pair<List<Uri>, Int> {
        if (candidateUris.isEmpty()) return emptyList<Uri>() to 0

        val existingTitles = existingLibrarySongs
            .map { normalizeSongTitle(it.title) }
            .filter { it.isNotEmpty() }
            .toMutableSet()

        val existingFingerprints = existingLibrarySongs
            .mapNotNull { song -> computeLocalFileFingerprint(song.uri.path) }
            .toMutableSet()

        val accepted = ArrayList<Uri>(candidateUris.size)
        var skippedDuplicates = 0

        for (uri in candidateUris) {
            val rawName = queryUriDisplayName(context, uri)
                ?: uri.lastPathSegment?.substringAfterLast('/')
                ?: ""
            val normName = normalizeSongTitle(rawName)
            val fingerprint = computeUriQuickFingerprint(context, uri)

            val isTitleDuplicate = normName.isNotEmpty() && normName in existingTitles
            val isContentDuplicate = fingerprint != null && fingerprint in existingFingerprints

            if (isTitleDuplicate || isContentDuplicate) {
                skippedDuplicates += 1
            } else {
                accepted.add(uri)
                if (normName.isNotEmpty()) existingTitles.add(normName)
                if (fingerprint != null) existingFingerprints.add(fingerprint)
            }
        }

        return accepted to skippedDuplicates
    }

    private fun stripCommonAudioExtension(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0 && name.length - dot <= 6) {
            name.substring(0, dot)
        } else {
            name
        }
    }

    private fun queryUriDisplayName(context: Context, uri: Uri): String? {
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

    private fun computeLocalFileFingerprint(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists() || !file.isFile || file.length() <= 0L) return null
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(16 * 1024)
            file.inputStream().use { input ->
                val read = input.read(buf)
                if (read > 0) md.update(buf, 0, read)
            }
            val headHex = md.digest().take(12).joinToString("") { "%02x".format(it) }
            "${file.length()}:$headHex"
        } catch (_: Exception) {
            null
        }
    }

    private fun computeUriQuickFingerprint(context: Context, uri: Uri): String? {
        return try {
            val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                afd.length
            } ?: -1L
            if (size <= 0L) return null

            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(16 * 1024)
            context.contentResolver.openInputStream(uri)?.use { input ->
                val read = input.read(buf)
                if (read > 0) md.update(buf, 0, read)
            } ?: return null

            val headHex = md.digest().take(12).joinToString("") { "%02x".format(it) }
            "$size:$headHex"
        } catch (_: Exception) {
            null
        }
    }
}
