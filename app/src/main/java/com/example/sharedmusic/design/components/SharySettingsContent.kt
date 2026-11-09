package com.example.sharedmusic.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sharedmusic.design.navigation.AppSettingsState
import com.example.sharedmusic.design.navigation.EQ_BAND_LABELS
import com.example.sharedmusic.design.navigation.EqPreset
import com.example.sharedmusic.design.navigation.VibeMotionLevel
import com.example.sharedmusic.design.theme.SharyDimens
import com.example.sharedmusic.design.theme.SharyThemeTokens
import java.util.Locale
import kotlin.math.roundToInt

// Экран 3-й вкладки «Настройки» (без нижнего круга "+"):
// - Загрузка только по Wi-Fi;
// - Только безопасные MP3-файлы (проверка расширения, MIME и сигнатуры файла);
// - Работающий 5-полосный эквалайзер с пресетами, усилением баса и громкостью плеера;
// - Скорость воспроизведения, таймер сна, анимация фона, удержание экрана в комнате и очистка кэша.
@Composable
fun SharySettingsContent(
    settings: AppSettingsState,
    isWifiCurrentlyConnected: Boolean,
    sleepTimerRemainingSec: Int,
    tempCacheSizeBytes: Long,
    onUpdateSettings: (AppSettingsState) -> Unit,
    onSelectSleepTimerMinutes: (Int) -> Unit,
    onClearTempCacheClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val surfaces = SharyThemeTokens.surfaces

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Настройки",
            color = surfaces.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        // 1. СЕТЬ, КОМНАТА И БЕЗОПАСНОСТЬ ФАЙЛОВ
        SettingsSectionCard(title = "Передача и безопасность файлов") {
            SettingsToggleRow(
                icon = { SharyWifiIcon(tint = surfaces.textPrimary, modifier = Modifier.size(20.dp)) },
                title = "Загрузка только по Wi-Fi",
                subtitle = if (isWifiCurrentlyConnected) {
                    "Сейчас Wi-Fi подключён • блокирует расход мобильных данных в комнате"
                } else {
                    "Сейчас мобильная сеть • скачивание и отправка в комнате ждут Wi-Fi"
                },
                checked = settings.wifiOnlyTransfers,
                onCheckedChange = { enabled ->
                    onUpdateSettings(settings.copy(wifiOnlyTransfers = enabled))
                }
            )

            SettingsDivider()

            SettingsToggleRow(
                icon = { SharySafeMp3ShieldIcon(tint = surfaces.textPrimary, modifier = Modifier.size(20.dp)) },
                title = "Только MP3 (безопасные файлы)",
                subtitle = "Проверка заголовка ID3/MPEG и блокировка посторонних форматов",
                checked = settings.onlySafeMp3Files,
                onCheckedChange = { enabled ->
                    onUpdateSettings(settings.copy(onlySafeMp3Files = enabled))
                }
            )

            SettingsDivider()

            SettingsToggleRow(
                icon = { SharyRoomIcon(tint = surfaces.textPrimary, modifier = Modifier.size(20.dp)) },
                title = "Не выключать экран в комнате",
                subtitle = "Удерживает подсветку экрана во время совместного прослушивания",
                checked = settings.keepScreenOnInRoom,
                onCheckedChange = { enabled ->
                    onUpdateSettings(settings.copy(keepScreenOnInRoom = enabled))
                }
            )
        }

        // 2. РАБОТАЮЩИЙ ЭКВАЛАЙЗЕР И АУДИОЭФФЕКТЫ
        SettingsSectionCard(title = "Эквалайзер и усиление звука") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    SharyEqualizerSlidersIcon(
                        tint = surfaces.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Системный эквалайзер",
                            color = surfaces.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (settings.equalizerEnabled) {
                                "Активен • пресет «${settings.eqPreset.labelRu}»"
                            } else {
                                "Выключен (чистый звук без обработки)"
                            },
                            color = surfaces.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                MonochromeSwitch(
                    checked = settings.equalizerEnabled,
                    onCheckedChange = { enabled ->
                        onUpdateSettings(settings.copy(equalizerEnabled = enabled))
                    }
                )
            }

            // Горизонтальная лента пресетов эквалайзера
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val visiblePresets = remember {
                    listOf(
                        EqPreset.FLAT,
                        EqPreset.BASS,
                        EqPreset.ROCK,
                        EqPreset.POP,
                        EqPreset.VOCAL,
                        EqPreset.ELECTRO
                    )
                }
                visiblePresets.forEach { preset ->
                    val selected = settings.eqPreset == preset
                    CompactOptionChip(
                        label = preset.labelRu,
                        selected = selected,
                        onClick = {
                            onUpdateSettings(
                                settings.copy(
                                    equalizerEnabled = true,
                                    eqPreset = preset,
                                    eqBandLevelsDb = preset.gainsDb
                                )
                            )
                        }
                    )
                }
            }

            // 5 вертикальных ползунков частот (-15 дБ .. +15 дБ)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (bandIndex in 0 until 5) {
                    val gainDb = settings.eqBandLevelsDb.getOrElse(bandIndex) { 0f }
                    val freqLabel = EQ_BAND_LABELS[bandIndex]

                    EqualizerVerticalBandSlider(
                        frequencyLabel = freqLabel,
                        gainDb = gainDb,
                        enabled = settings.equalizerEnabled,
                        onGainChange = { newDb ->
                            val updatedBands = settings.eqBandLevelsDb.toMutableList()
                            while (updatedBands.size < 5) updatedBands.add(0f)
                            updatedBands[bandIndex] = newDb
                            onUpdateSettings(
                                settings.copy(
                                    equalizerEnabled = true,
                                    eqPreset = EqPreset.CUSTOM,
                                    eqBandLevelsDb = updatedBands
                                )
                            )
                        }
                    )
                }
            }

            SettingsDivider()

            // Ползунок усиления низких частот (Bass Boost)
            SettingsHorizontalSliderRow(
                label = "Усиление баса",
                valueText = "${settings.bassBoostPercent}%",
                fraction = settings.bassBoostPercent / 100f,
                onFractionChange = { frac ->
                    val pct = (frac * 100f).roundToInt().coerceIn(0, 100)
                    onUpdateSettings(
                        settings.copy(
                            equalizerEnabled = if (pct > 0) true else settings.equalizerEnabled,
                            bassBoostPercent = pct
                        )
                    )
                }
            )

            // Ползунок собственной громкости плеера
            SettingsHorizontalSliderRow(
                label = "Громкость плеера",
                valueText = "${settings.playerVolumePercent}%",
                fraction = settings.playerVolumePercent / 100f,
                onFractionChange = { frac ->
                    val pct = (frac * 100f).roundToInt().coerceIn(0, 100)
                    onUpdateSettings(settings.copy(playerVolumePercent = pct))
                }
            )
        }

        // 3. СКОРОСТЬ ВОСПРОИЗВЕДЕНИЯ И ТАЙМЕР СНА
        SettingsSectionCard(title = "Воспроизведение и таймер сна") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Скорость воспроизведения",
                    color = surfaces.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                val speeds = remember { listOf(0.75f, 1.0f, 1.25f, 1.5f) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    speeds.forEach { spd ->
                        val isSelected = kotlin.math.abs(settings.playbackSpeed - spd) < 0.03f
                        CompactOptionChip(
                            label = "${spd}×",
                            selected = isSelected,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onUpdateSettings(settings.copy(playbackSpeed = spd))
                            }
                        )
                    }
                }
            }

            SettingsDivider()

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SharySleepTimerIcon(
                            tint = surfaces.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Таймер сна",
                            color = surfaces.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Text(
                        text = if (sleepTimerRemainingSec > 0) {
                            val m = sleepTimerRemainingSec / 60
                            val s = sleepTimerRemainingSec % 60
                            String.format(Locale.US, "Пауза через %d:%02d", m, s)
                        } else {
                            "Выключен"
                        },
                        color = if (sleepTimerRemainingSec > 0) surfaces.textPrimary else surfaces.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (sleepTimerRemainingSec > 0) FontWeight.SemiBold else FontWeight.Normal
                    )
                }

                val timerOptions = remember { listOf(0, 15, 30, 45, 60) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    timerOptions.forEach { mins ->
                        val active = if (mins == 0) {
                            sleepTimerRemainingSec <= 0
                        } else {
                            val approxMin = (sleepTimerRemainingSec + 59) / 60
                            sleepTimerRemainingSec > 0 && approxMin == mins
                        }
                        CompactOptionChip(
                            label = if (mins == 0) "Выкл" else "$mins м",
                            selected = active,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectSleepTimerMinutes(mins) }
                        )
                    }
                }
            }
        }

        // 4. АНИМАЦИЯ ФОНА И ПАМЯТЬ УСТРОЙСТВА
        SettingsSectionCard(title = "Анимация волн и хранилище") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Движение векторных волн на фоне",
                    color = surfaces.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VibeMotionLevel.entries.forEach { level ->
                        CompactOptionChip(
                            label = level.labelRu,
                            selected = settings.vibeMotionLevel == level,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onUpdateSettings(settings.copy(vibeMotionLevel = level))
                            }
                        )
                    }
                }
            }

            SettingsDivider()

            val cacheMb = tempCacheSizeBytes.toDouble() / (1024.0 * 1024.0)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Временный кэш передачи",
                        color = surfaces.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = String.format(Locale.US, "Занято: %.2f МБ (личная библиотека не затрагивается)", cacheMb),
                        color = surfaces.textSecondary,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                MonochromeActionPill(
                    text = "Очистить",
                    filled = false,
                    onClick = onClearTempCacheClick
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    val surfaces = SharyThemeTokens.surfaces
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(surfaces.songRowFill)
            .border(SharyDimens.GlassBorderWidth, surfaces.songRowBorder, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            color = surfaces.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        content()
    }
}

@Composable
private fun SettingsDivider() {
    val surfaces = SharyThemeTokens.surfaces
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(surfaces.songRowBorder)
    )
}

@Composable
private fun SettingsToggleRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val surfaces = SharyThemeTokens.surfaces
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            icon()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = surfaces.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    color = surfaces.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        MonochromeSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun MonochromeSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val surfaces = SharyThemeTokens.surfaces
    val trackColor by animateColorAsState(
        targetValue = if (checked) surfaces.playerAccent else surfaces.fieldFill,
        animationSpec = tween(140),
        label = "monoSwitchTrack"
    )
    val thumbColor = if (checked) surfaces.onAccent else surfaces.textSecondary

    Box(
        modifier = Modifier
            .width(46.dp)
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(trackColor)
            .border(
                width = 1.dp,
                color = if (checked) surfaces.textPrimary.copy(alpha = 0.28f) else surfaces.fieldBorder,
                shape = RoundedCornerShape(13.dp)
            )
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}

@Composable
private fun CompactOptionChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val surfaces = SharyThemeTokens.surfaces
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(shape)
            .background(if (selected) surfaces.playerAccent else surfaces.fieldFill)
            .border(
                width = 1.dp,
                color = if (selected) surfaces.textPrimary.copy(alpha = 0.28f) else surfaces.fieldBorder,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) surfaces.onAccent else surfaces.textPrimary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EqualizerVerticalBandSlider(
    frequencyLabel: String,
    gainDb: Float,
    enabled: Boolean,
    onGainChange: (Float) -> Unit
) {
    val surfaces = SharyThemeTokens.surfaces
    var sliderHeightPx by remember { mutableFloatStateOf(1f) }
    val latestOnGainChange by rememberUpdatedState(onGainChange)

    val norm = ((gainDb.coerceIn(-15f, 15f) + 15f) / 30f).coerceIn(0f, 1f)
    val dbText = when {
        gainDb > 0.2f -> String.format(Locale.US, "+%.0f", gainDb)
        gainDb < -0.2f -> String.format(Locale.US, "%.0f", gainDb)
        else -> "0"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(52.dp)
    ) {
        Text(
            text = "$dbText дБ",
            color = if (enabled) surfaces.textPrimary else surfaces.textSecondary,
            fontSize = 10.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Canvas(
            modifier = Modifier
                .width(34.dp)
                .height(112.dp)
                .onSizeChanged { sliderHeightPx = it.height.toFloat().coerceAtLeast(1f) }
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        val fracFromBottom = 1f - (pos.y / sliderHeightPx).coerceIn(0f, 1f)
                        val db = (fracFromBottom * 30f - 15f).roundToInt().toFloat()
                        latestOnGainChange(db.coerceIn(-15f, 15f))
                    }
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, _ ->
                        change.consume()
                        val fracFromBottom = 1f - (change.position.y / sliderHeightPx).coerceIn(0f, 1f)
                        val db = ((fracFromBottom * 30f - 15f) * 2f).roundToInt() / 2f
                        latestOnGainChange(db.coerceIn(-15f, 15f))
                    }
                }
        ) {
            val cx = size.width / 2f
            val padY = 8.dp.toPx()
            val topY = padY
            val botY = size.height - padY
            val midY = (topY + botY) / 2f
            val thumbY = botY - (botY - topY) * norm
            val trackStroke = 4.dp.toPx()

            // Неактивная дорожка
            drawLine(
                color = surfaces.playerTrackInactive,
                start = Offset(cx, topY),
                end = Offset(cx, botY),
                strokeWidth = trackStroke,
                cap = StrokeCap.Round
            )

            // Нулевая отметка 0 дБ
            drawLine(
                color = surfaces.textSecondary.copy(alpha = 0.40f),
                start = Offset(cx - 6.dp.toPx(), midY),
                end = Offset(cx + 6.dp.toPx(), midY),
                strokeWidth = 1.dp.toPx()
            )

            // Активный сегмент от 0 дБ до текущего усиления
            val activeColor = if (enabled) surfaces.textPrimary else surfaces.textSecondary
            drawLine(
                color = activeColor,
                start = Offset(cx, midY),
                end = Offset(cx, thumbY),
                strokeWidth = trackStroke,
                cap = StrokeCap.Round
            )

            drawCircle(
                color = activeColor,
                radius = 6.5.dp.toPx(),
                center = Offset(cx, thumbY)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = frequencyLabel,
            color = surfaces.textSecondary,
            fontSize = 10.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SettingsHorizontalSliderRow(
    label: String,
    valueText: String,
    fraction: Float,
    onFractionChange: (Float) -> Unit
) {
    val surfaces = SharyThemeTokens.surfaces
    var widthPx by remember { mutableFloatStateOf(1f) }
    val latestOnChange by rememberUpdatedState(onFractionChange)
    val clamped = fraction.coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = surfaces.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = valueText,
                color = surfaces.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        latestOnChange((offset.x / widthPx).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        latestOnChange((change.position.x / widthPx).coerceIn(0f, 1f))
                    }
                }
        ) {
            val cy = size.height / 2f
            val pad = 7.dp.toPx()
            val left = pad
            val right = (size.width - pad).coerceAtLeast(left + 1f)
            val thumbX = left + (right - left) * clamped
            val sw = 4.dp.toPx()

            drawLine(
                color = surfaces.playerTrackInactive,
                start = Offset(left, cy),
                end = Offset(right, cy),
                strokeWidth = sw,
                cap = StrokeCap.Round
            )
            if (thumbX > left + 0.5f) {
                drawLine(
                    color = surfaces.textPrimary,
                    start = Offset(left, cy),
                    end = Offset(thumbX, cy),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
            }
            drawCircle(
                color = surfaces.textPrimary,
                radius = 6.5.dp.toPx(),
                center = Offset(thumbX, cy)
            )
        }
    }
}
