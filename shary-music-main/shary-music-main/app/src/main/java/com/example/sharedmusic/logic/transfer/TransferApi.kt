package com.example.sharedmusic.logic.transfer

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Клиент Edge Function manage-transfer.
 * Использует только publishable/anon ключ — service_role остаётся на сервере.
 */
class TransferApi {

    companion object {
        // Те же значения, что в SupabaseStorage
        private const val SUPABASE_URL =
            "https://bihtoksworbnnisknylr.supabase.co"

        private const val SUPABASE_PUBLISHABLE_KEY =
            "sb_publishable_VJInQq_aGBeGpidzB68t3w_PyJ8Bepw"

        private const val FUNCTION_PATH = "/functions/v1/manage-transfer"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /**
     * Регистрирует передачу после успешной загрузки в Storage.
     * @param expectedRecipients uid слушателей (без отправителя)
     */
    fun register(
        roomCode: String,
        objectPath: String,
        downloadUrl: String,
        fileName: String,
        songTitle: String,
        senderUid: String,
        expectedRecipients: List<String>,
        ttlMinutes: Int = 10
    ): RegisterTransferResult {
        val body = JSONObject().apply {
            put("action", "register")
            put("room_code", roomCode)
            put("object_path", objectPath)
            put("download_url", downloadUrl)
            put("file_name", fileName)
            put("song_title", songTitle)
            put("sender_uid", senderUid)
            put("expected_recipients", JSONArray(expectedRecipients))
            put("ttl_minutes", ttlMinutes)
        }

        val response = post(body)

        if (!response.optBoolean("ok", false) && response.has("error")) {
            throw IOException("register: ${response.getString("error")}")
        }

        val transferId = response.optString("transfer_id", "")
        if (transferId.isBlank()) {
            throw IOException("register: сервер не вернул transfer_id")
        }

        return RegisterTransferResult(
            transferId = transferId,
            expiresAt = response.optString("expires_at", null),
            expectedCount = response.optInt("expected_count", expectedRecipients.size)
        )
    }

    /**
     * Подтверждает, что получатель успешно сохранил файл локально.
     */
    fun confirm(
        transferId: String,
        recipientUid: String
    ): ConfirmTransferResult {
        val body = JSONObject().apply {
            put("action", "confirm")
            put("transfer_id", transferId)
            put("recipient_uid", recipientUid)
        }

        val response = post(body)

        if (response.has("error") && !response.optBoolean("ok", false)) {
            throw IOException("confirm: ${response.getString("error")}")
        }

        return ConfirmTransferResult(
            status = response.optString("status", "pending"),
            confirmedCount = response.optInt("confirmed_count", 0),
            expectedCount = response.optInt("expected_count", 0),
            deleted = response.optBoolean("deleted", false)
        )
    }

    /**
     * Удаляет файл из Storage по object_path
     * (если confirm недоступен или transferId пустой).
     */
    fun deleteByPath(
        objectPath: String,
        transferId: String? = null
    ): Boolean {
        val body = JSONObject().apply {
            put("action", "delete_by_path")
            put("object_path", objectPath)
            if (!transferId.isNullOrBlank()) {
                put("transfer_id", transferId)
            }
        }
        val response = post(body)
        if (response.has("error") && !response.optBoolean("ok", false)) {
            throw IOException("delete_by_path: ${response.getString("error")}")
        }
        return response.optBoolean("ok", false)
    }

    /**
     * Запрос на очистку просроченных/завершённых передач.
     * Можно вызывать при старте приложения или периодически.
     */
    fun cleanup(): Boolean {
        val body = JSONObject().apply {
            put("action", "cleanup")
        }

        val response = post(body)
        return response.optBoolean("ok", false)
    }

    private fun post(json: JSONObject): JSONObject {
        val url = SUPABASE_URL.trimEnd('/') + FUNCTION_PATH

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $SUPABASE_PUBLISHABLE_KEY")
            .header("apikey", SUPABASE_PUBLISHABLE_KEY)
            .header("Content-Type", "application/json")
            .post(json.toString().toRequestBody(jsonMedia))
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                throw IOException(
                    "Edge Function HTTP ${response.code}: $text"
                )
            }

            if (text.isBlank()) {
                throw IOException("Edge Function вернул пустой ответ")
            }

            return JSONObject(text)
        }
    }
}
