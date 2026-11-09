package com.example.sharedmusic.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import com.example.sharedmusic.design.theme.SharyThemeTokens

// Область 1 (из 4): Задний план с полноэкранной векторной анимацией позади ВСЕХ объектов.
// Передаёт стабильный контроллер без ежекадровых рекомпозиций дочерних экранов.
@Composable
fun SharyBackgroundArea(
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    reactionTrigger: Int = 0,
    motionMultiplier: Float = 1f,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val surfaces = SharyThemeTokens.surfaces
    val vibeController = rememberSharyVibeController(
        isPlaying = isPlaying,
        reactionTrigger = reactionTrigger,
        motionMultiplier = motionMultiplier
    )
    val rootBounds = remember { SharyRootBoundsHolder() }

    CompositionLocalProvider(
        LocalSharyVibeController provides vibeController,
        LocalSharyRootBounds provides rootBounds
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .onSizeChanged {
                    rootBounds.widthPx = it.width.toFloat()
                    rootBounds.heightPx = it.height.toFloat()
                }
                .background(surfaces.backgroundBase)
        ) {
            SharyVibeBackground(
                controller = vibeController,
                modifier = Modifier.fillMaxSize()
            )

            content()
        }
    }
}
