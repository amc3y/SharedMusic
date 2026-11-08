package com.example.sharedmusic.design.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.sharedmusic.R
import com.example.sharedmusic.design.navigation.SharyTab
import com.example.sharedmusic.design.theme.SharyDimens
import com.example.sharedmusic.design.theme.SharyThemeTokens
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

// Область 4 (из 4): Нижняя полупрозрачная матовая панель навигации.
// На вкладке «Комната» ДО создания/входа в комнату (showCenterButtonAndNotch = false)
// впадина и центральная кнопка полностью убраны.
@Composable
fun SharyBottomArea(
    selectedTab: SharyTab,
    onTabSelect: (SharyTab) -> Unit,
    onAddSongsClick: () -> Unit,
    onPlayPauseClick: () -> Unit = {},
    isPlaying: Boolean = false,
    showCenterButtonAndNotch: Boolean = true,
    modifier: Modifier = Modifier,
    barHeight: Dp = SharyDimens.BottomAreaBarHeight,
    outerCornerRadius: Dp = SharyDimens.PanelOuterRadius,
    addButtonSize: Dp = SharyDimens.AddButtonSize,
    cutoutOuterRadius: Dp = SharyDimens.BottomNotchOuterRadius,
    cutoutShoulderRadius: Dp = SharyDimens.BottomNotchShoulderRadius,
    cutoutCenterOffsetY: Dp = SharyDimens.BottomNotchCenterOffsetY
) {
    val surfaces = SharyThemeTokens.surfaces
    val isRoomTab = selectedTab == SharyTab.ROOM

    val circleProtrusionAboveBar = if (showCenterButtonAndNotch) {
        (addButtonSize / 2) - cutoutCenterOffsetY
    } else {
        0.dp
    }
    val totalWrapperHeight = barHeight + circleProtrusionAboveBar

    val dockShape: Shape = remember(
        showCenterButtonAndNotch,
        outerCornerRadius,
        cutoutOuterRadius,
        cutoutShoulderRadius,
        cutoutCenterOffsetY
    ) {
        if (showCenterButtonAndNotch) {
            BottomDockNotchShape(
                cornerRadius = outerCornerRadius,
                cutoutRadius = cutoutOuterRadius,
                shoulderRadius = cutoutShoulderRadius,
                cutoutCenterOffsetY = cutoutCenterOffsetY
            )
        } else {
            RoundedCornerShape(outerCornerRadius)
        }
    }

    val centerInteraction = remember { MutableInteractionSource() }
    val isCenterPressed by centerInteraction.collectIsPressedAsState()
    val centerScaleState = rememberSharyInteractiveScaleState(
        isSelected = isRoomTab && isPlaying,
        isPressed = isCenterPressed,
        selectedScale = 1.05f,
        pressedScale = 1.10f
    )
    val centerContainerColorState = animateColorAsState(
        targetValue = if (isCenterPressed) {
            surfaces.addCircleContainerPressed
        } else {
            surfaces.addCircleContainer
        },
        animationSpec = tween(durationMillis = 110),
        label = "centerActionCircleColor"
    )

    val centerActionLabel = when {
        !isRoomTab -> stringResource(R.string.action_add_songs)
        isPlaying -> stringResource(R.string.action_pause)
        else -> stringResource(R.string.action_play)
    }

    Box(
        modifier = modifier
            .padding(
                start = SharyDimens.ScreenOuterMargin,
                end = SharyDimens.ScreenOuterMargin,
                bottom = SharyDimens.ScreenOuterMargin
            )
            .fillMaxWidth()
            .height(totalWrapperHeight)
    ) {
        OneUiGlassBox(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(barHeight),
            shape = dockShape,
            fillColor = surfaces.barFill,
            borderColor = surfaces.barBorder,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
        ) {
            if (showCenterButtonAndNotch) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(
                            SharyDimens.BottomNavIconSpacing,
                            Alignment.CenterHorizontally
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BottomTabItem(
                            tab = SharyTab.LIBRARY,
                            isSelected = selectedTab == SharyTab.LIBRARY,
                            onClick = { onTabSelect(SharyTab.LIBRARY) }
                        )
                        BottomTabItem(
                            tab = SharyTab.ROOM,
                            isSelected = selectedTab == SharyTab.ROOM,
                            onClick = { onTabSelect(SharyTab.ROOM) }
                        )
                    }

                    Spacer(modifier = Modifier.width(SharyDimens.BottomNotchHorizontalSpan))

                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(
                            SharyDimens.BottomNavIconSpacing,
                            Alignment.CenterHorizontally
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BottomTabItem(
                            tab = SharyTab.SETTINGS,
                            isSelected = selectedTab == SharyTab.SETTINGS,
                            onClick = { onTabSelect(SharyTab.SETTINGS) }
                        )
                        BottomTabItem(
                            tab = SharyTab.PROFILE,
                            isSelected = selectedTab == SharyTab.PROFILE,
                            onClick = { onTabSelect(SharyTab.PROFILE) }
                        )
                    }
                }
            } else {
                // До создания комнаты: без центральной впадины, 4 иконки аккуратно выстроены в ряд
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomTabItem(
                        tab = SharyTab.LIBRARY,
                        isSelected = selectedTab == SharyTab.LIBRARY,
                        onClick = { onTabSelect(SharyTab.LIBRARY) }
                    )
                    BottomTabItem(
                        tab = SharyTab.ROOM,
                        isSelected = selectedTab == SharyTab.ROOM,
                        onClick = { onTabSelect(SharyTab.ROOM) }
                    )
                    BottomTabItem(
                        tab = SharyTab.SETTINGS,
                        isSelected = selectedTab == SharyTab.SETTINGS,
                        onClick = { onTabSelect(SharyTab.SETTINGS) }
                    )
                    BottomTabItem(
                        tab = SharyTab.PROFILE,
                        isSelected = selectedTab == SharyTab.PROFILE,
                        onClick = { onTabSelect(SharyTab.PROFILE) }
                    )
                }
            }
        }

        // Центральный круг 70.dp (отображается только когда впадина и кнопка активны)
        if (showCenterButtonAndNotch) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(addButtonSize)
                    .graphicsLayer {
                        val s = centerScaleState.value
                        scaleX = s
                        scaleY = s
                    }
                    .semantics {
                        contentDescription = centerActionLabel
                        role = Role.Button
                    }
                    .clip(CircleShape)
                    .drawBehind {
                        drawRect(color = centerContainerColorState.value)
                    }
                    .clickable(
                        interactionSource = centerInteraction,
                        indication = null,
                        onClick = {
                            if (isRoomTab) {
                                onPlayPauseClick()
                            } else {
                                onAddSongsClick()
                            }
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(
                    targetState = isRoomTab,
                    animationSpec = tween(durationMillis = 150),
                    label = "centerIconMode"
                ) { roomMode ->
                    if (roomMode) {
                        SharyPlayPauseIcon(
                            isPlaying = isPlaying,
                            tint = surfaces.addCircleIcon
                        )
                    } else {
                        SharyPlusIcon(tint = surfaces.addCircleIcon)
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomTabItem(
    tab: SharyTab,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val surfaces = SharyThemeTokens.surfaces
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val label = stringResource(tab.labelResId)
    val iconScaleState = rememberSharyInteractiveScaleState(
        isSelected = isSelected,
        isPressed = isPressed
    )

    val circleColorState = animateColorAsState(
        targetValue = when {
            isSelected -> surfaces.navSelectionCircleSelected
            isPressed -> surfaces.navSelectionCirclePressed
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 120),
        label = "tabSelectionCircle"
    )

    val iconTint by animateColorAsState(
        targetValue = if (isSelected || isPressed) {
            surfaces.navIconSelected
        } else {
            surfaces.navIconInactive
        },
        animationSpec = tween(durationMillis = 120),
        label = "tabIconTint"
    )

    val targetCircleDiameter = SharyDimens.BottomNavSelectionCircleSize

    Box(
        modifier = Modifier
            .size(SharyDimens.BottomNavTouchTargetSize)
            .semantics {
                contentDescription = label
                role = Role.Tab
                selected = isSelected
            }
            .drawBehind {
                val maxRadius = size.minDimension / 2f
                val desiredRadius = (targetCircleDiameter.toPx() / 2f).coerceAtMost(maxRadius)
                drawCircle(
                    color = circleColorState.value,
                    radius = desiredRadius
                )
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                val s = iconScaleState.value
                scaleX = s
                scaleY = s
            },
            contentAlignment = Alignment.Center
        ) {
            SharyBottomTabIcon(
                tab = tab,
                tint = iconTint
            )
        }
    }
}

class BottomDockNotchShape(
    private val cornerRadius: Dp,
    private val cutoutRadius: Dp,
    private val shoulderRadius: Dp,
    private val cutoutCenterOffsetY: Dp = 0.dp
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = with(density) {
        val w = size.width
        val h = size.height

        val corner = min(cornerRadius.toPx(), min(w, h) / 2f)
        val rc = cutoutRadius.toPx().coerceIn(1f, min(w * 0.42f, h * 0.92f))
        val rs = shoulderRadius.toPx().coerceAtLeast(1f)
        val cx = w / 2f
        val yc = cutoutCenterOffsetY.toPx()

        val dy = rs - yc
        val hypot = rc + rs
        val dx = sqrt((hypot * hypot - dy * dy).coerceAtLeast(0f))

        val leftShoulderX = cx - dx
        val rightShoulderX = cx + dx

        if (leftShoulderX <= corner || rightShoulderX >= w - corner) {
            val fallback = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(0f, 0f, w, h),
                        radiusX = corner,
                        radiusY = corner
                    )
                )
            }
            return Outline.Generic(fallback)
        }

        val radToDeg = (180.0 / PI).toFloat()
        val leftShoulderEndDeg = atan2(-dy, dx) * radToDeg
        val leftShoulderSweepDeg = leftShoulderEndDeg - (-90f)

        val cutoutStartDeg = atan2(dy, -dx) * radToDeg
        val cutoutEndDeg = atan2(dy, dx) * radToDeg
        val cutoutSweepDeg = cutoutEndDeg - cutoutStartDeg

        val rightShoulderStartDeg = atan2(-dy, -dx) * radToDeg
        val rightShoulderSweepDeg = -90f - rightShoulderStartDeg

        val path = Path().apply {
            moveTo(corner, 0f)
            lineTo(leftShoulderX, 0f)

            arcTo(
                rect = Rect(
                    left = leftShoulderX - rs,
                    top = 0f,
                    right = leftShoulderX + rs,
                    bottom = 2f * rs
                ),
                startAngleDegrees = -90f,
                sweepAngleDegrees = leftShoulderSweepDeg,
                forceMoveTo = false
            )

            arcTo(
                rect = Rect(
                    left = cx - rc,
                    top = yc - rc,
                    right = cx + rc,
                    bottom = yc + rc
                ),
                startAngleDegrees = cutoutStartDeg,
                sweepAngleDegrees = cutoutSweepDeg,
                forceMoveTo = false
            )

            arcTo(
                rect = Rect(
                    left = rightShoulderX - rs,
                    top = 0f,
                    right = rightShoulderX + rs,
                    bottom = 2f * rs
                ),
                startAngleDegrees = rightShoulderStartDeg,
                sweepAngleDegrees = rightShoulderSweepDeg,
                forceMoveTo = false
            )

            lineTo(w - corner, 0f)

            arcTo(
                rect = Rect(w - 2f * corner, 0f, w, 2f * corner),
                startAngleDegrees = -90f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            lineTo(w, h - corner)

            arcTo(
                rect = Rect(w - 2f * corner, h - 2f * corner, w, h),
                startAngleDegrees = 0f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            lineTo(corner, h)

            arcTo(
                rect = Rect(0f, h - 2f * corner, 2f * corner, h),
                startAngleDegrees = 90f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            lineTo(0f, corner)

            arcTo(
                rect = Rect(0f, 0f, 2f * corner, 2f * corner),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            close()
        }

        Outline.Generic(path)
    }
}
