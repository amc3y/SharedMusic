package com.example.sharedmusic.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sharedmusic.R
import com.example.sharedmusic.design.theme.SharyDimens
import com.example.sharedmusic.design.theme.SharyThemeTokens
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

// Область 2 (из 4): Верхняя полупрозрачная панель с плавным овальным вырезом посередине.
// В обычном режиме: логотип SHARY слева и поиск справа.
// В активной Комнате: значок выхода [→ слева, а справа копирование + #КОД + поделиться (как на фото).
@Composable
fun SharyTopArea(
    modifier: Modifier = Modifier,
    isSearchActive: Boolean = false,
    activeRoomCode: String? = null,
    onLogoClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onExitRoomClick: () -> Unit = {},
    onCopyRoomCodeClick: () -> Unit = {},
    onShareRoomCodeClick: () -> Unit = {},
    outerMargin: PaddingValues = PaddingValues(
        start = SharyDimens.ScreenOuterMargin,
        top = SharyDimens.ScreenOuterMargin,
        end = SharyDimens.ScreenOuterMargin,
        bottom = 0.dp
    ),
    centerContent: @Composable BoxScope.() -> Unit = {}
) {
    val surfaces = SharyThemeTokens.surfaces

    val topShape = remember {
        TopCameraOvalNotchShape(
            cornerRadius = SharyDimens.PanelOuterRadius,
            baseCutoutRadius = SharyDimens.TopCameraNotchBaseRadius,
            shoulderRadius = SharyDimens.TopCameraNotchShoulderRadius,
            cutoutCenterOffsetY = SharyDimens.TopCameraNotchCenterOffsetY,
            ovalStretchX = SharyDimens.TopCameraNotchOvalStretchX
        )
    }

    OneUiGlassBox(
        shape = topShape,
        fillColor = surfaces.barFill,
        borderColor = surfaces.barBorder,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        modifier = modifier
            .padding(outerMargin)
            .fillMaxWidth()
            .height(SharyDimens.TopAreaHeight)
    ) {
        if (activeRoomCode != null) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TopBarActionButton(
                    label = stringResource(R.string.action_leave_room),
                    isSelected = false,
                    onClick = onExitRoomClick
                ) {
                    SharyExitRoomIcon(
                        tint = surfaces.navIconSelected,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                    content = centerContent
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    TopBarCompactIconButton(
                        label = stringResource(R.string.action_copy_room_code),
                        onClick = onCopyRoomCodeClick
                    ) {
                        SharyCopyRoomCodeIcon(
                            tint = surfaces.navIconSelected,
                            modifier = Modifier.size(21.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = "#$activeRoomCode",
                        color = surfaces.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onCopyRoomCodeClick)
                            .padding(horizontal = 3.dp, vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.width(2.dp))

                    TopBarCompactIconButton(
                        label = stringResource(R.string.action_share_room_code),
                        onClick = onShareRoomCodeClick
                    ) {
                        SharyShareNodesIcon(
                            tint = surfaces.navIconSelected,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TopBarActionButton(
                    label = stringResource(R.string.action_app_logo),
                    isSelected = false,
                    onClick = onLogoClick
                ) {
                    SharyLogoMark(
                        primaryColor = surfaces.logoPrimaryBar,
                        secondaryColor = surfaces.logoSecondaryBar,
                        modifier = Modifier.size(SharyDimens.TopBarIconSize)
                    )
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                    content = centerContent
                )

                TopBarActionButton(
                    label = stringResource(R.string.action_search),
                    isSelected = isSearchActive,
                    onClick = onSearchClick
                ) {
                    SharySearchIcon(
                        tint = surfaces.navIconSelected,
                        modifier = Modifier.size(SharyDimens.TopBarIconSize)
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBarCompactIconButton(
    label: String,
    onClick: () -> Unit,
    iconContent: @Composable () -> Unit
) {
    val surfaces = SharyThemeTokens.surfaces
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scaleState = rememberSharyInteractiveScaleState(
        isSelected = false,
        isPressed = isPressed
    )
    val circleColorState = animateColorAsState(
        targetValue = if (isPressed) surfaces.navSelectionCirclePressed else Color.Transparent,
        animationSpec = tween(durationMillis = 100),
        label = "topBarCompactPress"
    )

    Box(
        modifier = Modifier
            .size(32.dp)
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .drawBehind {
                drawCircle(
                    color = circleColorState.value,
                    radius = size.minDimension / 2f
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
                val s = scaleState.value
                scaleX = s
                scaleY = s
            },
            contentAlignment = Alignment.Center
        ) {
            iconContent()
        }
    }
}

@Composable
private fun TopBarActionButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    iconContent: @Composable () -> Unit
) {
    val surfaces = SharyThemeTokens.surfaces
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scaleState = rememberSharyInteractiveScaleState(
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
        label = "topBarPressCircle"
    )

    Box(
        modifier = Modifier
            .size(SharyDimens.TopBarTouchTargetSize)
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .drawBehind {
                drawCircle(
                    color = circleColorState.value,
                    radius = size.minDimension / 2f
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
                val s = scaleState.value
                scaleX = s
                scaleY = s
            },
            contentAlignment = Alignment.Center
        ) {
            iconContent()
        }
    }
}

class TopCameraOvalNotchShape(
    private val cornerRadius: Dp,
    private val baseCutoutRadius: Dp,
    private val shoulderRadius: Dp,
    private val cutoutCenterOffsetY: Dp,
    private val ovalStretchX: Float
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = with(density) {
        val w = size.width
        val h = size.height
        val corner = min(cornerRadius.toPx(), min(w, h) / 2f)

        val rc = baseCutoutRadius.toPx().coerceAtLeast(4f)
        val rs = shoulderRadius.toPx().coerceAtLeast(2f)
        val yc = cutoutCenterOffsetY.toPx()
        val k = ovalStretchX.coerceAtLeast(1f)
        val cx = w / 2f

        val dy = rs - yc
        val hypot = rc + rs
        val dxBase = sqrt((hypot * hypot - dy * dy).coerceAtLeast(0f))
        val dxStretched = dxBase * k

        val leftShoulderX = cx - dxStretched
        val rightShoulderX = cx + dxStretched

        if (leftShoulderX <= corner + 4f || rightShoulderX >= w - corner - 4f) {
            val simple = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(0f, 0f, w, h),
                        radiusX = corner,
                        radiusY = corner
                    )
                )
            }
            return Outline.Generic(simple)
        }

        val radToDeg = (180.0 / PI).toFloat()
        val leftShoulderEndDeg = atan2(-dy, dxBase) * radToDeg
        val leftShoulderSweepDeg = leftShoulderEndDeg - (-90f)

        val cutoutStartDeg = atan2(dy, -dxBase) * radToDeg
        val cutoutEndDeg = atan2(dy, dxBase) * radToDeg
        val cutoutSweepDeg = cutoutEndDeg - cutoutStartDeg

        val rightShoulderStartDeg = atan2(-dy, -dxBase) * radToDeg
        val rightShoulderSweepDeg = -90f - rightShoulderStartDeg

        val rsX = rs * k
        val rcX = rc * k

        val path = Path().apply {
            moveTo(corner, 0f)
            lineTo(leftShoulderX, 0f)

            arcTo(
                rect = Rect(
                    left = leftShoulderX - rsX,
                    top = 0f,
                    right = leftShoulderX + rsX,
                    bottom = 2f * rs
                ),
                startAngleDegrees = -90f,
                sweepAngleDegrees = leftShoulderSweepDeg,
                forceMoveTo = false
            )

            arcTo(
                rect = Rect(
                    left = cx - rcX,
                    top = yc - rc,
                    right = cx + rcX,
                    bottom = yc + rc
                ),
                startAngleDegrees = cutoutStartDeg,
                sweepAngleDegrees = cutoutSweepDeg,
                forceMoveTo = false
            )

            arcTo(
                rect = Rect(
                    left = rightShoulderX - rsX,
                    top = 0f,
                    right = rightShoulderX + rsX,
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
