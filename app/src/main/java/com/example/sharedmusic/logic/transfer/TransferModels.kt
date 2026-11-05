package com.example.sharedmusic.logic.transfer

/**
 * Результат загрузки файла в Supabase Storage.
 */
data class UploadResult(
    val downloadUrl: String,
    /** Путь объекта в bucket, например "uuid_track.mp3" */
    val objectPath: String,
    val fileName: String
)

/**
 * Ответ сервера на регистрацию передачи.
 */
data class RegisterTransferResult(
    val transferId: String,
    val expiresAt: String?,
    val expectedCount: Int
)

/**
 * Ответ сервера на подтверждение скачивания.
 */
data class ConfirmTransferResult(
    val status: String,
    val confirmedCount: Int,
    val expectedCount: Int,
    val deleted: Boolean
)
