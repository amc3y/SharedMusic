package com.example.sharedmusic.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.example.sharedmusic.R
import com.example.sharedmusic.design.theme.SharyDimens
import com.example.sharedmusic.design.theme.SharyThemeTokens

// Полоса из 4 значков перелистывания и механики музыки для вкладки «Комната»
@Composable
fun SharyRoomTransportRow(
    isShuffleEnabled: Boolean,
    isRepeatEnabled: Boolean,
    onShuffleClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onRepeatClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SharyDimens.ScreenOuterMargin)
            .height(SharyDimens.RoomTransportBandHeight),
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
            RoomTransportButton(
                label = stringResource(R.string.action_shuffle),
                isToggled = isShuffleEnabled,
                isPrimary = false,
                onClick = onShuffleClick
            ) { tint ->
                SharyShuffleIcon(tint = tint)
            }

            RoomTransportButton(
                label = stringResource(R.string.action_previous),
                isToggled = false,
                isPrimary = true,
                onClick = onPreviousClick
            ) { tint ->
                SharySkipPreviousIcon(tint = tint)
            }
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
            RoomTransportButton(
                label = stringResource(R.string.action_next),
                isToggled = false,
                isPrimary = true,
                onClick = onNextClick
            ) { tint ->
                SharySkipNextIcon(tint = tint)
            }

            RoomTransportButton(
                label = stringResource(R.string.action_repeat),
                isToggled = isRepeatEnabled,
                isPrimary = false,
                onClick = onRepeatClick
            ) { tint ->
                SharyRepeatIcon(tint = tint)
            }
        }
    }
}

@Composable
private fun RoomTransportButton(
    label: String,
    isToggled: Boolean,
    isPrimary: Boolean,
    onClick: () -> Unit,
    iconContent: @Composable (tint: Color) -> Unit
) {
    val surfaces = SharyThemeTokens.surfaces
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scaleState = rememberSharyInteractiveScaleState(
        isSelected = isToggled,
        isPressed = isPressed
    )

    val circleColorState = animateColorAsState(
        targetValue = when {
            isToggled -> surfaces.navSelectionCircleSelected
            isPressed -> surfaces.navSelectionCirclePressed
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 120),
        label = "roomTransportCircle"
    )

    val iconTint by animateColorAsState(
        targetValue = when {
            isToggled || isPrimary || isPressed -> surfaces.transportPrimaryIcon
            else -> surfaces.transportSecondaryIcon
        },
        animationSpec = tween(durationMillis = 120),
        label = "roomTransportTint"
    )

    val targetDiameter = SharyDimens.RoomTransportSelectionCircleSize

    Box(
        modifier = Modifier
            .size(SharyDimens.RoomTransportTouchTargetSize)
            .semantics {
                contentDescription = label
                role = Role.Button
                selected = isToggled
            }
            .drawBehind {
                val maxRadius = size.minDimension / 2f
                val desiredRadius = (targetDiameter.toPx() / 2f).coerceAtMost(maxRadius)
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
                val s = scaleState.value
                scaleX = s
                scaleY = s
            },
            contentAlignment = Alignment.Center
        ) {
            iconContent(iconTint)
        }
    }
}
