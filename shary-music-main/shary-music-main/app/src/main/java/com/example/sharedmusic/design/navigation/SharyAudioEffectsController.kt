package com.example.sharedmusic.design.navigation

import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.sharedmusic.logic.viewmodel.MusicViewModel
import kotlin.math.roundToInt

// Мост реальных аудиоэффектов (аппаратный эквалайзер Android, усиление баса, громкость и скорость воспроизведения),
// подключающийся к ExoPlayer внутри MusicViewModel без изменений файлов в logic/ и без использования @UnstableApi.
@Composable
fun SharyAudioEffectsBridge(
    musicViewModel: MusicViewModel,
    settings: AppSettingsState,
    isInActiveRoom: Boolean
) {
    val exoPlayer = remember(musicViewModel) { extractExoPlayerSafely(musicViewModel) }
    var audioSessionId by remember { mutableIntStateOf(AudioManager.AUDIO_SESSION_ID_GENERATE) }
    val latestSettings by rememberUpdatedState(settings)
    val latestInRoom by rememberUpdatedState(isInActiveRoom)

    // Слушаем актуальный audioSessionId у ExoPlayer через стабильный Player.Listener
    DisposableEffect(exoPlayer) {
        if (exoPlayer == null) return@DisposableEffect onDispose {}

        val refreshSessionId: () -> Unit = {
            val sid = readAudioSessionIdSafely(exoPlayer)
            if (sid > 0 && sid != audioSessionId) {
                audioSessionId = sid
            }
        }

        refreshSessionId()

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                refreshSessionId()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                refreshSessionId()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                refreshSessionId()
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Применяем громкость и скорость воспроизведения напрямую к Player
    LaunchedEffect(exoPlayer, settings.playerVolumePercent, settings.playbackSpeed, isInActiveRoom) {
        val player = exoPlayer ?: return@LaunchedEffect
        runCatching {
            player.volume = (settings.playerVolumePercent / 100f).coerceIn(0f, 1f)
            val effectiveSpeed = if (isInActiveRoom) 1.0f else settings.playbackSpeed.coerceIn(0.75f, 1.5f)
            player.setPlaybackSpeed(effectiveSpeed)
        }
    }

    // Создаём и удерживаем системные Equalizer и BassBoost на текущей аудиосессии плеера
    val holder = remember { AudioFxHolder() }

    DisposableEffect(audioSessionId) {
        holder.release()
        if (audioSessionId > 0) {
            holder.attach(audioSessionId)
            holder.apply(latestSettings)
        }
        onDispose {
            holder.release()
        }
    }

    // Мгновенно обновляем полосы частот и бас при движении ползунков в Настройках
    LaunchedEffect(
        audioSessionId,
        settings.equalizerEnabled,
        settings.eqBandLevelsDb,
        settings.bassBoostPercent
    ) {
        holder.apply(settings)
        exoPlayer?.let { player ->
            runCatching {
                player.setPlaybackSpeed(
                    if (latestInRoom) 1.0f else settings.playbackSpeed.coerceIn(0.75f, 1.5f)
                )
            }
        }
    }
}

private class AudioFxHolder {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    fun attach(sessionId: Int) {
        runCatching {
            equalizer = Equalizer(0, sessionId)
        }
        runCatching {
            bassBoost = BassBoost(0, sessionId)
        }
    }

    fun apply(settings: AppSettingsState) {
        val eq = equalizer
        if (eq != null) {
            runCatching {
                val active = settings.equalizerEnabled
                eq.enabled = active
                if (active) {
                    val bandCount = eq.numberOfBands.toInt()
                    val range = eq.bandLevelRange
                    val minMb = range.getOrNull(0)?.toInt() ?: -1500
                    val maxMb = range.getOrNull(1)?.toInt() ?: 1500
                    val uiBands = settings.eqBandLevelsDb

                    for (b in 0 until bandCount) {
                        val pos = if (bandCount <= 1) {
                            0f
                        } else {
                            b.toFloat() * (uiBands.size - 1).coerceAtLeast(1) / (bandCount - 1).toFloat()
                        }
                        val i0 = pos.toInt().coerceIn(0, uiBands.lastIndex)
                        val i1 = (i0 + 1).coerceIn(0, uiBands.lastIndex)
                        val frac = pos - i0
                        val db = uiBands[i0] + (uiBands[i1] - uiBands[i0]) * frac
                        val targetMb = (db * 100f).roundToInt().coerceIn(minMb, maxMb).toShort()
                        eq.setBandLevel(b.toShort(), targetMb)
                    }
                }
            }
        }

        val bb = bassBoost
        if (bb != null) {
            runCatching {
                val boostActive = settings.equalizerEnabled && settings.bassBoostPercent > 0
                bb.enabled = boostActive
                if (boostActive && bb.strengthSupported) {
                    val strength = (settings.bassBoostPercent.coerceIn(0, 100) * 10).toShort()
                    bb.setStrength(strength)
                }
            }
        }
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        equalizer = null
        bassBoost = null
    }
}

private fun extractExoPlayerSafely(viewModel: MusicViewModel): ExoPlayer? {
    return try {
        val mpField = MusicViewModel::class.java.getDeclaredField("musicPlayer").apply {
            isAccessible = true
        }
        val musicPlayerObj = mpField.get(viewModel) ?: return null
        val exoField = musicPlayerObj.javaClass.getDeclaredField("player").apply {
            isAccessible = true
        }
        exoField.get(musicPlayerObj) as? ExoPlayer
    } catch (_: Exception) {
        null
    }
}

private fun readAudioSessionIdSafely(player: ExoPlayer): Int {
    return try {
        val method = ExoPlayer::class.java.getMethod("getAudioSessionId")
        (method.invoke(player) as? Int) ?: 0
    } catch (_: Exception) {
        0
    }
}
