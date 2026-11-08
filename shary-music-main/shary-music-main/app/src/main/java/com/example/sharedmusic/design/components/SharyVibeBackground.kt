package com.example.sharedmusic.design.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.example.sharedmusic.design.theme.SharySurfacePalette
import com.example.sharedmusic.design.theme.SharyThemeTokens
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private const val WAVE_LAYER_COUNT = 5
private const val WAVE_SAMPLE_POINTS = 36
private const val RAY_COUNT = 8
private const val TWO_PI = (2.0 * PI).toFloat()
private const val UNIT_RADIUS = 100f

// Контроллер анимации, хранящий изменяемые float-состояния.
// Чтение .floatValue происходит ТОЛЬКО внутри фазы отрисовки (Canvas / drawBehind),
// благодаря чему дерево Compose вообще не пересобирается (0 рекомпозиций в секунду и плавные 60/120 FPS).
@Stable
class SharyVibeController {
    val phaseRad = mutableFloatStateOf(0.85f)
    val shimmerPhaseRad = mutableFloatStateOf(0.40f)
    val waveIntensity = mutableFloatStateOf(0.78f)
    val baseBrightness = mutableFloatStateOf(0.80f)
    val pulse = mutableFloatStateOf(0f)
}

// Хранит размеры корневого контейнера без вызова рекомпозиции
class SharyRootBoundsHolder {
    var widthPx: Float = 0f
    var heightPx: Float = 0f
}

val LocalSharyVibeController = compositionLocalOf { SharyVibeController() }
val LocalSharyRootBounds = compositionLocalOf { SharyRootBoundsHolder() }

// Предварительно созданные кисти градиентов (0 выделений памяти на кадре -> без микрофризов GC)
class VibeCachedBrushes(surfaces: SharySurfacePalette) {
    val cloudNebula: Brush = Brush.radialGradient(
        0.0f to surfaces.vibeDeepNebula,
        0.46f to surfaces.vibeMidSilk.copy(alpha = 0.45f),
        1.0f to Color.Transparent,
        center = Offset.Zero,
        radius = UNIT_RADIUS
    )

    val cloudAura: Brush = Brush.radialGradient(
        0.0f to surfaces.vibeDeepAura,
        0.48f to surfaces.vibeMidSilk.copy(alpha = 0.40f),
        1.0f to Color.Transparent,
        center = Offset.Zero,
        radius = UNIT_RADIUS
    )

    val rayBeam: Brush = Brush.linearGradient(
        0.0f to surfaces.vibeRayGlow,
        0.45f to surfaces.vibeMidSilk.copy(alpha = 0.45f),
        1.0f to Color.Transparent,
        start = Offset.Zero,
        end = Offset(UNIT_RADIUS, 0f)
    )

    val milkShimmer: Brush = Brush.radialGradient(
        0.0f to surfaces.milkShimmerSpot,
        0.55f to surfaces.milkShimmerSpot.copy(alpha = surfaces.milkShimmerSpot.alpha * 0.4f),
        1.0f to Color.Transparent,
        center = Offset.Zero,
        radius = UNIT_RADIUS
    )
}

@Composable
fun rememberVibeCachedBrushes(surfaces: SharySurfacePalette): VibeCachedBrushes {
    return remember(surfaces) { VibeCachedBrushes(surfaces) }
}

// Запускает плавный цикл анимации без рекомпозиций экранов
@Composable
fun rememberSharyVibeController(
    isPlaying: Boolean,
    reactionTrigger: Int,
    motionMultiplier: Float = 1f
): SharyVibeController {
    val controller = remember { SharyVibeController() }

    LaunchedEffect(isPlaying, motionMultiplier) {
        val m = motionMultiplier.coerceIn(0f, 1.8f)
        val startIntensity = controller.waveIntensity.floatValue
        val targetIntensity = if (m <= 0.01f) {
            0.35f
        } else {
            (if (isPlaying) 1.28f else 0.78f) * (0.45f + 0.55f * m)
        }
        val startBrightness = controller.baseBrightness.floatValue
        val targetBrightness = if (m <= 0.01f) {
            0.45f
        } else {
            (if (isPlaying) 1.06f else 0.80f) * (0.55f + 0.45f * m)
        }

        val anim = Animatable(0f)
        anim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
        ) {
            val t = value
            controller.waveIntensity.floatValue = startIntensity + (targetIntensity - startIntensity) * t
            controller.baseBrightness.floatValue = startBrightness + (targetBrightness - startBrightness) * t
        }
    }

    LaunchedEffect(isPlaying, motionMultiplier) {
        if (motionMultiplier <= 0.01f) return@LaunchedEffect
        val speed = (if (isPlaying) 0.92f else 0.28f) * motionMultiplier
        var lastFrameNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastFrameNanos != 0L) {
                    val deltaSec = ((frameNanos - lastFrameNanos) / 1_000_000_000f)
                        .coerceIn(0f, 0.05f)
                    controller.phaseRad.floatValue =
                        (controller.phaseRad.floatValue + deltaSec * speed) % TWO_PI
                    controller.shimmerPhaseRad.floatValue =
                        (controller.shimmerPhaseRad.floatValue + deltaSec * 0.22f * motionMultiplier) % TWO_PI
                }
                lastFrameNanos = frameNanos
            }
        }
    }

    LaunchedEffect(reactionTrigger) {
        if (reactionTrigger > 0) {
            val pulseAnim = Animatable(controller.pulse.floatValue)
            pulseAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing)
            ) {
                controller.pulse.floatValue = value
            }
            pulseAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
            ) {
                controller.pulse.floatValue = value
            }
        }
    }

    return controller
}

// Лёгкая полноэкранная векторная сцена на ОДНОМ Canvas без тяжёлых аппаратных RenderEffect blur:
// мягкое размытие достигается аналитическими радиальными/линейными градиентами без просадок FPS
@Composable
fun SharyVibeBackground(
    controller: SharyVibeController,
    modifier: Modifier = Modifier
) {
    val surfaces = SharyThemeTokens.surfaces
    val brushes = rememberVibeCachedBrushes(surfaces)

    val wavePaths = remember { Array(WAVE_LAYER_COUNT) { Path() } }
    val xs = remember { FloatArray(WAVE_SAMPLE_POINTS) }
    val ys = remember { FloatArray(WAVE_SAMPLE_POINTS) }
    val unitRayPath = remember {
        Path().apply {
            moveTo(0f, 0f)
            lineTo(UNIT_RADIUS, -11.5f)
            lineTo(UNIT_RADIUS, 11.5f)
            close()
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val phi = controller.phaseRad.floatValue
        val intensity = controller.waveIntensity.floatValue
        val brightness = controller.baseBrightness.floatValue
        val pulse = controller.pulse.floatValue

        val w = size.width
        val h = size.height

        // 1. Мягкие объёмные облака размытия и световые лучи
        drawFastCloudsAndRays(
            phi = phi,
            brightness = brightness,
            pulse = pulse,
            sceneWidth = w,
            sceneHeight = h,
            brushes = brushes,
            unitRayPath = unitRayPath
        )

        // 2. Плавные органические волны на весь экран (2 лёгких прохода без тяжёлых круглых фасок)
        val pulseScale = 1f + 0.13f * pulse
        val flashBoost = 0.24f * pulse
        val centerX = w * 0.50f
        val centerY = h * 0.49f
        val baseRx = w * 0.59f * pulseScale
        val baseRy = h * 0.46f * pulseScale

        val silkWidthPx = 11.dp.toPx()
        val crestWidthPx = 1.4.dp.toPx()

        for (layer in 0 until WAVE_LAYER_COUNT) {
            buildOrganicWavePath(
                outPath = wavePaths[layer],
                xs = xs,
                ys = ys,
                layerIndex = layer,
                phase = phi,
                intensity = intensity,
                centerX = centerX,
                centerY = centerY,
                baseRx = baseRx,
                baseRy = baseRy
            )

            val weight = 1f - layer * 0.12f
            val silkAlpha = ((0.18f * brightness + flashBoost * 0.55f) * weight).coerceIn(0f, 0.58f)
            val crestAlpha = ((0.27f * brightness + flashBoost * 0.80f) * weight).coerceIn(0f, 0.76f)

            drawPath(
                path = wavePaths[layer],
                color = surfaces.vibeMidSilk.copy(alpha = silkAlpha),
                style = Stroke(width = silkWidthPx)
            )
            drawPath(
                path = wavePaths[layer],
                color = surfaces.vibeBrightCrest.copy(alpha = crestAlpha),
                style = Stroke(width = crestWidthPx)
            )
        }
    }
}

// Быстрая отрисовка облаков свечения в общих координатах экрана (используется и на фоне, и как матовая дымка внутри 3 панелей)
internal fun DrawScope.drawSyncedVibeClouds(
    phi: Float,
    brightness: Float,
    pulse: Float,
    sceneWidth: Float,
    sceneHeight: Float,
    brushes: VibeCachedBrushes,
    alphaMultiplier: Float,
    radiusScale: Float = 1f
) {
    val maxDim = max(sceneWidth, sceneHeight).coerceAtLeast(1f)
    val pulseScale = (1f + 0.14f * pulse) * radiusScale
    val flash = 0.24f * pulse

    val c0x = sceneWidth * (0.50f + 0.09f * sin(phi))
    val c0y = sceneHeight * (0.46f + 0.08f * cos(phi * 0.8f))

    val c1x = sceneWidth * (0.25f + 0.15f * cos(phi + 0.8f))
    val c1y = sceneHeight * (0.20f + 0.10f * sin(phi))

    val c2x = sceneWidth * (0.76f + 0.14f * sin(phi + 1.9f))
    val c2y = sceneHeight * (0.72f + 0.12f * cos(phi))

    val r0 = maxDim * 0.60f * pulseScale
    val r1 = maxDim * 0.46f * pulseScale
    val r2 = maxDim * 0.48f * pulseScale

    val a0 = ((0.54f * brightness + flash) * alphaMultiplier).coerceIn(0f, 0.92f)
    val a1 = ((0.40f * brightness + flash) * alphaMultiplier).coerceIn(0f, 0.85f)
    val a2 = ((0.42f * brightness + flash) * alphaMultiplier).coerceIn(0f, 0.85f)

    drawScaledUnitCircle(cx = c0x, cy = c0y, radius = r0, brush = brushes.cloudNebula, alpha = a0)
    drawScaledUnitCircle(cx = c1x, cy = c1y, radius = r1, brush = brushes.cloudAura, alpha = a1)
    drawScaledUnitCircle(cx = c2x, cy = c2y, radius = r2, brush = brushes.cloudNebula, alpha = a2)
}

private fun DrawScope.drawFastCloudsAndRays(
    phi: Float,
    brightness: Float,
    pulse: Float,
    sceneWidth: Float,
    sceneHeight: Float,
    brushes: VibeCachedBrushes,
    unitRayPath: Path
) {
    drawSyncedVibeClouds(
        phi = phi,
        brightness = brightness,
        pulse = pulse,
        sceneWidth = sceneWidth,
        sceneHeight = sceneHeight,
        brushes = brushes,
        alphaMultiplier = 1f
    )

    val maxDim = max(sceneWidth, sceneHeight).coerceAtLeast(1f)
    val coreX = sceneWidth * (0.50f + 0.09f * sin(phi))
    val coreY = sceneHeight * (0.46f + 0.08f * cos(phi * 0.8f))
    val rayAlpha = (0.18f * brightness + 0.18f * pulse).coerceIn(0f, 0.55f)
    val radToDeg = (180.0 / PI).toFloat()

    for (r in 0 until RAY_COUNT) {
        val angleRad = (TWO_PI * r / RAY_COUNT) +
            0.30f * sin(phi + r * 0.8f) +
            phi * (if (r % 2 == 0) 1f else -1f)
        val rayLen = maxDim * (0.54f + 0.18f * sin(phi * 2f + r * 1.4f)) * (1f + 0.12f * pulse)
        val s = rayLen / UNIT_RADIUS

        withTransform({
            translate(left = coreX, top = coreY)
            rotate(degrees = angleRad * radToDeg, pivot = Offset.Zero)
            scale(scaleX = s, scaleY = s, pivot = Offset.Zero)
        }) {
            drawPath(
                path = unitRayPath,
                brush = brushes.rayBeam,
                alpha = rayAlpha
            )
        }
    }
}

internal fun DrawScope.drawScaledUnitCircle(
    cx: Float,
    cy: Float,
    radius: Float,
    brush: Brush,
    alpha: Float = 1f
) {
    if (radius <= 1f || alpha <= 0.002f) return
    val s = radius / UNIT_RADIUS
    withTransform({
        translate(left = cx, top = cy)
        scale(scaleX = s, scaleY = s, pivot = Offset.Zero)
    }) {
        drawCircle(
            brush = brush,
            radius = UNIT_RADIUS,
            center = Offset.Zero,
            alpha = alpha
        )
    }
}

private fun buildOrganicWavePath(
    outPath: Path,
    xs: FloatArray,
    ys: FloatArray,
    layerIndex: Int,
    phase: Float,
    intensity: Float,
    centerX: Float,
    centerY: Float,
    baseRx: Float,
    baseRy: Float
) {
    outPath.rewind()

    val offset = layerIndex * 1.15f
    val layerScale = 0.40f + layerIndex * 0.155f
    val driftX = centerX + baseRx * 0.08f * sin(phase + offset)
    val driftY = centerY + baseRy * 0.07f * cos(phase - offset * 0.7f)

    for (i in 0 until WAVE_SAMPLE_POINTS) {
        val theta = TWO_PI * i / WAVE_SAMPLE_POINTS
        val waveModulation = 1f + intensity * (
            0.15f * sin(2f * theta + phase + offset) +
                0.11f * cos(3f * theta - 2f * phase + 1.6f * offset) +
                0.07f * sin(5f * theta + 2f * phase - 0.9f * offset)
            )

        val twistedTheta = theta + 0.17f * intensity * sin(phase + theta + offset)
        xs[i] = driftX + baseRx * layerScale * waveModulation * cos(twistedTheta)
        ys[i] = driftY + baseRy * layerScale * waveModulation * sin(twistedTheta)
    }

    outPath.moveTo(xs[0], ys[0])
    for (i in 0 until WAVE_SAMPLE_POINTS) {
        val prev = (i - 1 + WAVE_SAMPLE_POINTS) % WAVE_SAMPLE_POINTS
        val curr = i
        val next = (i + 1) % WAVE_SAMPLE_POINTS
        val next2 = (i + 2) % WAVE_SAMPLE_POINTS

        val cp1x = xs[curr] + (xs[next] - xs[prev]) / 6f
        val cp1y = ys[curr] + (ys[next] - ys[prev]) / 6f
        val cp2x = xs[next] - (xs[next2] - xs[curr]) / 6f
        val cp2y = ys[next] - (ys[next2] - ys[curr]) / 6f

        outPath.cubicTo(cp1x, cp1y, cp2x, cp2y, xs[next], ys[next])
    }
    outPath.close()
}
