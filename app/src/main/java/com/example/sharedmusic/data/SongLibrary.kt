package com.example.sharedmusic.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.sharedmusic.model.Song
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Локальная библиотека песен.
 * Файлы копируются в filesDir/library/, каталог — library/songs.json.
 * Так список переживает перезапуск, а content:// URI не «протухают».
 */
class SongLibrary(context: Context) {

    private val appContext = context.applicationContext
    private val libraryDir = File(appContext.filesDir, "library")
    private val catalogFile = File(libraryDir, "songs.json")

    init {
        if (!libraryDir.exists()) {
            libraryDir.mkdirs()
        }
    }

    fun loadSongs(): List<Song> {
        if (!catalogFile.exists()) return emptyList()

        return try {
            val root = JSONArray(catalogFile.readText())
            val result = mutableListOf<Song>()

            for (i in 0 until root.length()) {
                val obj = root.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                if (id.isBlank()) continue
                val title = obj.optString("title", "Без названия")
                val path = obj.optString("path")
                val isRemote = obj.optBoolean("isRemote", false)
                val durationMs = obj.optLong("durationMs", 0L)

                if (path.isBlank()) continue

                val file = File(path)
                if (!file.exists() || !file.isFile || file.length() == 0L) {
                    continue
                }

                result.add(
                    Song(
                        id = id,
                        title = title,
                        uri = Uri.fromFile(file),
                        durationMs = durationMs,
                        isRemote = isRemote
                    )
                )
            }

            // Перезаписываем каталог без битых записей
            if (result.size != root.length()) {
                saveSongs(result)
            }

            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveSongs(songs: List<Song>) {
        if (!libraryDir.exists()) {
            libraryDir.mkdirs()
        }

        val array = JSONArray()

        for (song in songs) {
            val path = song.uri.path ?: continue
            val file = File(path)
            if (!file.exists()) continue

            array.put(
                JSONObject().apply {
                    put("id", song.id)
                    put("title", song.title)
                    put("path", file.absolutePath)
                    put("isRemote", song.isRemote)
                    put("durationMs", song.durationMs)
                }
            )
        }

        catalogFile.writeText(array.toString())
    }

    /**
     * Копирует выбранный пользователем файл (content:// или file://)
     * в library/ и возвращает Song.
     */
    fun importFromUri(uri: Uri, preferredTitle: String? = null): Song {
        val displayName = preferredTitle
            ?: queryDisplayName(uri)
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "track"

        val safeTitle = sanitizeTitle(displayName)
        val extension = guessExtension(displayName, uri)
        val id = UUID.randomUUID().toString()
        val dest = File(libraryDir, "${id}$extension")

        copyUriToFile(uri, dest)

        return Song(
            id = id,
            title = stripExtension(safeTitle),
            uri = Uri.fromFile(dest),
            isRemote = false
        )
    }

    /**
     * Кладёт уже скачанный (или временный) файл в library/.
     * Если файл уже внутри libraryDir — не копирует повторно.
     */
    fun importFromFile(
        source: File,
        title: String,
        songId: String? = null,
        isRemote: Boolean = false
    ): Song {
        if (!source.exists() || !source.isFile || source.length() == 0L) {
            throw IOException("Файл не найден или пуст")
        }

        val id = songId ?: UUID.randomUUID().toString()
        val extension = guessExtension(source.name, null)
        val dest = File(libraryDir, "${id}$extension")

        val finalFile = when {
            source.absolutePath == dest.absolutePath -> source
            source.parentFile?.absolutePath == libraryDir.absolutePath -> source
            else -> {
                source.copyTo(dest, overwrite = true)
                // room_tracks можно оставить как кэш, не удаляем source насильно
                dest
            }
        }

        return Song(
            id = id,
            title = title.ifBlank { stripExtension(source.name) },
            uri = Uri.fromFile(finalFile),
            isRemote = isRemote
        )
    }

    fun deleteSongFiles(song: Song) {
        val path = song.uri.path ?: return
        val file = File(path)
        if (
            file.exists() &&
            file.parentFile?.absolutePath == libraryDir.absolutePath
        ) {
            file.delete()
        }
    }

    private fun copyUriToFile(uri: Uri, dest: File) {
        val input = appContext.contentResolver.openInputStream(uri)
            ?: throw IOException("Не удалось открыть файл")

        input.use { source ->
            dest.outputStream().use { output ->
                source.copyTo(output)
            }
        }

        if (!dest.exists() || dest.length() == 0L) {
            dest.delete()
            throw IOException("Не удалось скопировать файл")
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            appContext.contentResolver.query(
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

    private fun guessExtension(name: String, uri: Uri?): String {
        val fromName = name.substringAfterLast('.', "")
            .lowercase()
            .takeIf { it.length in 1..5 && it.all { c -> c.isLetterOrDigit() } }

        if (fromName != null) return ".$fromName"

        val mime = uri?.let {
            try {
                appContext.contentResolver.getType(it)
            } catch (_: Exception) {
                null
            }
        }

        return when (mime) {
            "audio/mpeg" -> ".mp3"
            "audio/mp4", "audio/aac" -> ".m4a"
            "audio/wav", "audio/x-wav" -> ".wav"
            "audio/ogg", "application/ogg" -> ".ogg"
            "audio/flac" -> ".flac"
            else -> ".audio"
        }
    }

    private fun sanitizeTitle(name: String): String {
        val base = name.substringAfterLast('/').trim()
        return base.ifBlank { "track" }.take(180)
    }

    private fun stripExtension(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0 && name.length - dot <= 5) {
            name.substring(0, dot)
        } else {
            name
        }
    }

}
