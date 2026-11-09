package com.example.sharedmusic.design.navigation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min

private const val PROFILE_PREFS = "shary_user_profile"
private const val KEY_DISPLAY_NAME = "display_name"
private const val KEY_AVATAR_BASE64 = "avatar_base64"

data class LocalUserProfile(
    val displayName: String = "",
    val avatarBase64: String = "",
    val avatarBitmap: ImageBitmap? = null
)

object UserProfileStore {

    private val remoteAvatarCache = object : LruCache<String, ImageBitmap>(64) {}

    fun loadProfile(context: Context): LocalUserProfile {
        val prefs = context.applicationContext.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_DISPLAY_NAME, "").orEmpty()
        val base64 = prefs.getString(KEY_AVATAR_BASE64, "").orEmpty()
        val bitmap = decodeAvatarBase64(base64)
        return LocalUserProfile(
            displayName = name,
            avatarBase64 = base64,
            avatarBitmap = bitmap
        )
    }

    fun saveDisplayName(context: Context, name: String): LocalUserProfile {
        val cleaned = name.trim().take(24)
        val prefs = context.applicationContext.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DISPLAY_NAME, cleaned).apply()
        return loadProfile(context)
    }

    suspend fun importAvatarFromUri(context: Context, uri: Uri): LocalUserProfile? {
        return withContext(Dispatchers.IO) {
            try {
                val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@withContext null
                if (rawBytes.isEmpty()) return@withContext null

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
                val maxSide = max(bounds.outWidth, bounds.outHeight)
                if (maxSide <= 0) return@withContext null

                var sample = 1
                while (maxSide / (sample * 2) >= 160) {
                    sample *= 2
                }
                val decoded = BitmapFactory.decodeByteArray(
                    rawBytes,
                    0,
                    rawBytes.size,
                    BitmapFactory.Options().apply { inSampleSize = sample }
                ) ?: return@withContext null

                // Центрированная квадратная обрезка и сжатие до 112x112 для быстрой передачи в комнату и чат
                val side = min(decoded.width, decoded.height)
                val xOff = (decoded.width - side) / 2
                val yOff = (decoded.height - side) / 2
                val square = Bitmap.createBitmap(decoded, xOff, yOff, side, side)
                val scaled = Bitmap.createScaledBitmap(square, 112, 112, true)

                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 76, out)
                val jpegBytes = out.toByteArray()

                val profileDir = File(context.applicationContext.filesDir, "profile")
                if (!profileDir.exists()) profileDir.mkdirs()
                File(profileDir, "avatar.jpg").writeBytes(jpegBytes)

                val encoded = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
                val prefs = context.applicationContext.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
                prefs.edit().putString(KEY_AVATAR_BASE64, encoded).apply()

                LocalUserProfile(
                    displayName = prefs.getString(KEY_DISPLAY_NAME, "").orEmpty(),
                    avatarBase64 = encoded,
                    avatarBitmap = scaled.asImageBitmap()
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    fun clearAvatar(context: Context): LocalUserProfile {
        val profileDir = File(context.applicationContext.filesDir, "profile")
        val avatarFile = File(profileDir, "avatar.jpg")
        if (avatarFile.exists()) avatarFile.delete()
        val prefs = context.applicationContext.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_AVATAR_BASE64, "").apply()
        return loadProfile(context)
    }

    fun decodeAvatarBase64(base64: String): ImageBitmap? {
        if (base64.isBlank()) return null
        val key = "${base64.length}:${base64.hashCode()}"
        synchronized(remoteAvatarCache) {
            remoteAvatarCache.get(key)?.let { return it }
        }
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            if (bmp != null) {
                synchronized(remoteAvatarCache) {
                    remoteAvatarCache.put(key, bmp)
                }
            }
            bmp
        } catch (_: Exception) {
            null
        }
    }
}
