package com.example.sharedmusic.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Один оборот пластинки — 2 секунды (≈33 rpm, как у настоящего винила) */
private const val ROTATION_PERIOD_MS = 2000L

/**
 * Крутящаяся виниловая пластинка для главного экрана.
 *
 * Пока isSpinning = true — маркер на наклейке бежит по кругу (оборот 2 секунды).
 * Пауза — диск замирает в текущей позиции.
 *
 * Дизайн (по mockup «home concept»): чёрный винил с бороздками, розовая
 * центральная наклейка с нотой, световое отражение.
 *
 * Примечания по реализации:
 * - параметр назван discSize, чтобы не затенять `size` холста внутри Canvas;
 * - вращение = позиция маркерной точки по sin/cos (кольца симметричны —
 *   «крутить» холст не нужно; так и надёжнее в новых версиях Compose).
 */
@Composable
fun RecordDisc(
    isSpinning: Boolean,
    modifier: Modifier = Modifier,
    discSize: Dp = 240.dp,
) {
    var angle by remember { mutableStateOf(0f) }

    // Пока играет — крутим диск, кадр за кадром
    LaunchedEffect(isSpinning) {
        if (!isSpinning) return@LaunchedEffect
        var lastTimeNs = withFrameNanos { it }
        while (true) {
            val nowNs = withFrameNanos { it }
            val deltaMs = (nowNs - lastTimeNs) / 1_000_000f
            lastTimeNs = nowNs
            angle += deltaMs / ROTATION_PERIOD_MS * 360f
            if (angle >= 360f) angle -= 360f
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    val holeColor = MaterialTheme.colorScheme.background

    Box(modifier = modifier.size(discSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            // `size` здесь — размер холста (Size), collision с параметром устранён
            val center = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f

            // 1. Тело диска (винил) — тёмный градиент для глубины
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF1E1E25), Color(0xFF111114)),
                    center = center,
                    radius = r,
                ),
                center = center,
                radius = r,
            )

            // 2. Бороздки — тонкие концентрические окружности
            for (i in 1..7) {
                drawCircle(
                    color = Color(0xFF272730),
                    center = center,
                    radius = r * (0.42f + i * 0.075f),
                    style = Stroke(width = 1.5f),
                )
            }

            // 3. Розовая наклейка (круг симметричен — статична)
            drawCircle(color = primary, center = center, radius = r * 0.30f)

            // 4. Отверстие под вал
            drawCircle(color = holeColor, center = center, radius = r * 0.045f)

            // 5. Маркерная точка — единственное «движущееся» на диске;
            //    позиция считается по sin/cos от текущего угла
            val rad = Math.toRadians(angle.toDouble())
            drawCircle(
                color = Color.White.copy(alpha = 0.7f),
                center = center + Offset(
                    (r * 0.21f * Math.sin(rad)).toFloat(),
                    (-r * 0.21f * Math.cos(rad)).toFloat(),
                ),
                radius = r * 0.035f,
            )

            // 6. Статичное световое отражение («свет» не крутится)
            drawArc(
                color = Color.White.copy(alpha = 0.05f),
                startAngle = 200f,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = Offset(center.x - r * 0.90f, center.y - r * 0.90f),
                size = Size(r * 1.80f, r * 1.80f),
                style = Stroke(width = r * 0.12f, cap = StrokeCap.Round),
            )
        }

        // 7. Нота на наклейке
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size((discSize.value * 0.13f).dp),
        )
    }
}

@Preview(name = "Пластинка (крутится)", showBackground = true, backgroundColor = 0xFF0B0B0F)
@Composable
private fun RecordDiscPreview() {
    RecordDisc(isSpinning = true, discSize = 200.dp)
}