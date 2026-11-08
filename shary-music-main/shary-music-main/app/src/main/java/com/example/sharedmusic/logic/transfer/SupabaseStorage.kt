package com.example.sharedmusic.logic.transfer

import android.net.Uri
import com.example.sharedmusic.logic.transfer.UploadResult
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import okio.Buffer
import okio.source
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLConnection
import java.util.UUID
import java.util.concurrent.TimeUnit

class SupabaseStorage {

    companion object {
        // ЗАМЕНИ ЭТИ ДВЕ СТРОКИ ДАННЫМИ СВОЕГО ПРОЕКТА (если отличаются)
        private const val SUPABASE_URL =
            "https://bihtoksworbnnisknylr.supabase.co"

        private const val SUPABASE_PUBLISHABLE_KEY =
            "sb_publishable_VJInQq_aGBeGpidzB68t3w_PyJ8Bepw"

        private const val BUCKET_NAME = "shared-music"

        private const val MAX_FILE_BYTES =
            50L * 1024L * 1024L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .callTimeout(10, TimeUnit.MINUTES)
        .build()

    /**
     * Загружает музыкальный файл в Supabase Storage
     * и возвращает публичную ссылку + object_path для регистрации передачи.
     */
    fun upload(
        file: File,
        remoteFileName: String = file.name,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): UploadResult {
        checkConfiguration()

        if (!file.exists() || !file.isFile) {
            throw IOException("Файл не найден: ${file.absolutePath}")
        }

        if (file.length() == 0L) {
            throw IOException("Нельзя загрузить пустой файл")
        }

        if (file.length() > MAX_FILE_BYTES) {
            throw IOException(
                "Размер файла превышает бесплатный лимит 50 МБ"
            )
        }

        val safeName = sanitizeFileName(remoteFileName)
        val objectName = "${UUID.randomUUID()}_$safeName"
        val encodedObjectName = Uri.encode(objectName)

        val baseUrl = SUPABASE_URL.trimEnd('/')

        val uploadUrl =
            "$baseUrl/storage/v1/object/" +
                    "$BUCKET_NAME/$encodedObjectName"

        val fileBody = ProgressFileRequestBody(
            file = file,
            fileName = safeName,
            onProgress = onProgress
        )

        val request = Request.Builder()
            .url(uploadUrl)
            .header("apikey", SUPABASE_PUBLISHABLE_KEY)
            .header("Authorization", "Bearer $SUPABASE_PUBLISHABLE_KEY")
            .header("x-upsert", "false")
            .post(fileBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorText = response.body?.string().orEmpty()

                throw IOException(
                    "Supabase: HTTP ${response.code}: $errorText"
                )
            }
        }

        val downloadUrl =
            "$baseUrl/storage/v1/object/public/" +
                    "$BUCKET_NAME/$encodedObjectName"

        return UploadResult(
            downloadUrl = downloadUrl,
            objectPath = objectName,
            fileName = safeName
        )
    }

    /**
     * Скачивает файл из публичного bucket
     * во временный файл с последующим сохранением.
     */
    fun download(
        downloadUrl: String,
        destinationFile: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): File {
        checkConfiguration()

        val baseUrl = SUPABASE_URL.trimEnd('/')
        val projectUri = Uri.parse(baseUrl)
        val downloadUri = Uri.parse(downloadUrl)

        val expectedPath =
            "/storage/v1/object/public/$BUCKET_NAME/"

        if (
            downloadUri.scheme != "https" ||
            downloadUri.host != projectUri.host ||
            !downloadUri.encodedPath.orEmpty()
                .startsWith(expectedPath)
        ) {
            throw IOException(
                "Недопустимая ссылка на музыкальный файл"
            )
        }

        val parent = destinationFile.parentFile
            ?: throw IOException("Не определена папка для файла")

        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Не удалось создать папку для музыки")
        }

        if (destinationFile.exists()) {
            throw IOException("Файл с таким именем уже существует")
        }

        val temporaryFile = File(
            parent,
            "${destinationFile.name}.part"
        )

        val request = Request.Builder()
            .url(downloadUrl)
            .header("apikey", SUPABASE_PUBLISHABLE_KEY)
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body
                    ?: throw IOException("Сервер вернул пустой ответ")

                if (!response.isSuccessful) {
                    throw IOException(
                        "Supabase: HTTP ${response.code}: " +
                                body.string()
                    )
                }

                val contentType =
                    body.contentType()?.toString().orEmpty()

                if (
                    contentType.contains(
                        "application/json",
                        ignoreCase = true
                    ) ||
                    contentType.contains(
                        "text/html",
                        ignoreCase = true
                    )
                ) {
                    throw IOException(
                        "Вместо аудиофайла сервер вернул ошибку"
                    )
                }

                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(temporaryFile).use { output ->
                        val buffer = ByteArray(16 * 1024)

                        while (true) {
                            val count = input.read(buffer)

                            if (count == -1) break

                            output.write(buffer, 0, count)
                            downloadedBytes += count

                            onProgress(downloadedBytes, totalBytes)
                        }

                        output.flush()
                    }
                }
            }

            if (
                !temporaryFile.exists() ||
                temporaryFile.length() == 0L
            ) {
                throw IOException("Скачанный файл пуст")
            }

            if (!temporaryFile.renameTo(destinationFile)) {
                temporaryFile.copyTo(
                    target = destinationFile,
                    overwrite = false
                )
                temporaryFile.delete()
            }

            return destinationFile
        } catch (e: Exception) {
            temporaryFile.delete()
            destinationFile.delete()
            throw e
        }
    }

    private fun checkConfiguration() {
        if (
            SUPABASE_URL.contains("YOUR_PROJECT_REF") ||
            SUPABASE_PUBLISHABLE_KEY.contains(
                "YOUR_SUPABASE_PUBLISHABLE_KEY"
            ) ||
            !SUPABASE_URL.startsWith("https://") ||
            SUPABASE_PUBLISHABLE_KEY.isBlank()
        ) {
            throw IOException(
                "Укажи Project URL и Publishable key в SupabaseStorage.kt"
            )
        }
    }

    private fun sanitizeFileName(name: String): String {
        val safeName = name.map { char ->
            if (
                char.isLetterOrDigit() ||
                char in " ._()-"
            ) {
                char
            } else {
                '_'
            }
        }.joinToString("")
            .trim()
            .take(180)

        return safeName.ifBlank { "track.mp3" }
    }

    private class ProgressFileRequestBody(
        private val file: File,
        private val fileName: String,
        private val onProgress: (Long, Long) -> Unit
    ) : RequestBody() {

        override fun contentType(): MediaType {
            return URLConnection
                .guessContentTypeFromName(fileName)
                ?.toMediaType()
                ?: "application/octet-stream".toMediaType()
        }

        override fun contentLength(): Long {
            return file.length()
        }

        override fun writeTo(sink: okio.BufferedSink) {
            var uploadedBytes = 0L
            val totalBytes = file.length()

            file.source().use { source ->
                val buffer = Buffer()

                while (true) {
                    val count = source.read(buffer, 8192L)

                    if (count == -1L) break

                    sink.write(buffer, count)
                    uploadedBytes += count

                    onProgress(uploadedBytes, totalBytes)
                }
            }
        }
    }
}
