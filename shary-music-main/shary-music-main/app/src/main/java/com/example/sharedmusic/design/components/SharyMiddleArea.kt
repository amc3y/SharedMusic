package com.example.sharedmusic.design.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.sharedmusic.design.theme.SharyDimens
import com.example.sharedmusic.design.theme.SharyThemeTokens

// Область 3 (из 4): Средняя полупрозрачная серая область с острым правым верхним углом (topEnd = 0.dp)
@Composable
fun SharyMiddleArea(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = SharyDimens.PanelOuterRadius,
    contentPadding: PaddingValues = PaddingValues(SharyDimens.PanelInnerPadding),
    content: @Composable BoxScope.() -> Unit = {}
) {
    val surfaces = SharyThemeTokens.surfaces

    // Острый правый верхний угол (topEnd = 0.dp), остальные 3 угла скруглены (16.dp)
    val shape = RoundedCornerShape(
        topStart = cornerRadius,
        topEnd = 0.dp,
        bottomEnd = cornerRadius,
        bottomStart = cornerRadius
    )

    OneUiGlassBox(
        modifier = modifier
            .padding(horizontal = SharyDimens.ScreenOuterMargin)
            .fillMaxWidth(),
        shape = shape,
        fillColor = surfaces.middleFill,
        borderColor = surfaces.middleBorder,
        contentPadding = contentPadding,
        content = content
    )
}
