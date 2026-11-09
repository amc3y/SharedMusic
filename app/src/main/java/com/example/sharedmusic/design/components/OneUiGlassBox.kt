package com.example.sharedmusic.design.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.example.sharedmusic.design.theme.SharyDimens
import com.example.sharedmusic.design.theme.SharyThemeTokens
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private class PanelOffsetHolder {
    var x: Float = 0f
    var y: Float = 0f
}

// Быстрая и отзывчивая пружина масштабирования значков при нажатии/выборе.
// Возвращает State<Float>, чтобы читать значение внутри graphicsLayer { ... } БЕЗ рекомпозиции иконки!
@Composable
fun rememberSharyInteractiveScaleState(
    isSelected: Boolean = false,
    isPressed: Boolean = false,
    selectedScale: Float = 1.11f,
    pressedScale: Float = 1.15f
): State<Float> {
    val target = when {
        isPressed && isSelected -> max(selectedScale, pressedScale) + 0.03f
        isPressed -> pressedScale
        isSelected -> selectedScale
        else -> 1.0f
    }
    return animateFloatAsState(
        targetValue = target,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = 1100f
        ),
        label = "sharyInteractiveScale"
    )
}

// Полупрозрачный матовый контейнер для всех 3 серых областей без тяжёлого RenderEffect blur:
// вся отрисовка мутного свечения и лёгкого переливания Milk выполняется в фазе drawBehind (0 рекомпозиций!)
@Composable
fun OneUiGlassBox(
    shape: Shape,
    fillColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(SharyDimens.PanelInnerPadding),
    content: @Composable BoxScope.() -> Unit = {}
) {
    val surfaces = SharyThemeTokens.surfaces
    val vibeController = LocalSharyVibeController.current
    val rootBounds = LocalSharyRootBounds.current
    val brushes = rememberVibeCachedBrushes(surfaces)
    val offsetHolder = remember { PanelOffsetHolder() }

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                offsetHolder.x = pos.x
                offsetHolder.y = pos.y
            }
            .clip(shape)
            .drawBehind {
                val phi = vibeController.phaseRad.floatValue
                val shimmerPhi = vibeController.shimmerPhaseRad.floatValue
                val brightness = vibeController.baseBrightness.floatValue
                val pulse = vibeController.pulse.floatValue

                val w = size.width
                val h = size.height
                val fullW = if (rootBounds.widthPx > 0f) rootBounds.widthPx else w
                val fullH = if (rootBounds.heightPx > 0f) rootBounds.heightPx else h

                // 1. Мягкая матовая вуаль приглушает чёткие линии волн позади панели, сохраняя полупрозрачность
                drawRect(color = surfaces.backgroundBase.copy(alpha = 0.46f))

                // 2. Широкое размытое свечение фоновой анимации в точных координатах экрана
                withTransform({
                    translate(left = -offsetHolder.x, top = -offsetHolder.y)
                }) {
                    drawSyncedVibeClouds(
                        phi = phi,
                        brightness = brightness,
                        pulse = pulse,
                        sceneWidth = fullW,
                        sceneHeight = fullH,
                        brushes = brushes,
                        alphaMultiplier = surfaces.glassCloudAlpha,
                        radiusScale = 1.18f
                    )
                }

                // 3. Основной полупрозрачный серый цвет стеклянной области
                drawRect(color = fillColor)

                // 4. Почти незаметное плавное переливание цветом Milk в разных участках области
                val r = max(w, h).coerceAtLeast(1f) * 0.56f
                val p = shimmerPhi + offsetHolder.y * 0.0035f
                drawScaledUnitCircle(
                    cx = w * (0.24f + 0.18f * sin(p)),
                    cy = h * (0.32f + 0.22f * cos(p * 1.3f)),
                    radius = r,
                    brush = brushes.milkShimmer
                )
                drawScaledUnitCircle(
                    cx = w * (0.78f + 0.16f * cos(p + 2.1f)),
                    cy = h * (0.68f + 0.20f * sin(p + 1.4f)),
                    radius = r * 0.85f,
                    brush = brushes.milkShimmer
                )
            }
            .border(
                width = SharyDimens.GlassBorderWidth,
                color = borderColor,
                shape = shape
            )
            .padding(contentPadding),
        content = content
    )
}
