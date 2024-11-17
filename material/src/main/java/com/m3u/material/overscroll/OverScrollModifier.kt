package com.m3u.material.overscroll

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun Modifier.overScrollScale(
    overScrollState: OverScrollState = LocalOverScrollState.current,
    maxOffset: Float = overScrollState.maxOffset,
    maxScaleMultiple: Float = 1.5f
) = this.then(
    OverscrollGraphicsLayerModifier(
        overScrollState = overScrollState,
        maxOffset = maxOffset,
        maxScaleMultiple = maxScaleMultiple,
        effect = OverScrollEffect.Scale
    )
)

@Composable
fun Modifier.overScrollScaleCenter(
    overScrollState: OverScrollState = LocalOverScrollState.current,
    maxOffset: Float = overScrollState.maxOffset,
    maxScaleMultiple: Float = 1.5f
) = this.then(
    OverscrollGraphicsLayerModifier(
        overScrollState = overScrollState,
        maxOffset = maxOffset,
        maxScaleMultiple = maxScaleMultiple,
        effect = OverScrollEffect.ScaleCenter
    )
)

@Composable
fun Modifier.overScrollParallaxVertical(
    overScrollState: OverScrollState = LocalOverScrollState.current,
    maxOffset: Float = overScrollState.maxOffset,
    maxParallaxOffset: Float = 100f
) = this.then(
    OverscrollGraphicsLayerModifier(
        overScrollState = overScrollState,
        maxOffset = maxOffset,
        maxParallaxOffset = maxParallaxOffset,
        effect = OverScrollEffect.ParallaxVertical
    )
)

@Composable
fun Modifier.overScrollParallaxHorizontal(
    overScrollState: OverScrollState = LocalOverScrollState.current,
    maxOffset: Float = overScrollState.maxOffset,
    maxParallaxOffset: Float = 100f
) = this.then(
    OverscrollGraphicsLayerModifier(
        overScrollState = overScrollState,
        maxOffset = maxOffset,
        maxParallaxOffset = maxParallaxOffset,
        effect = OverScrollEffect.ParallaxHorizontal
    )
)

@Composable
fun Modifier.overScrollAlpha(
    overScrollState: OverScrollState = LocalOverScrollState.current,
    maxOffset: Float = overScrollState.maxOffset,
    finalAlpha: Float = 0f
) = this.then(
    OverscrollGraphicsLayerModifier(
        overScrollState = overScrollState,
        maxOffset = maxOffset,
        finalAlpha = finalAlpha,
        effect = OverScrollEffect.Alpha
    )
)

@Composable
fun Modifier.overScrollRotationCenter(
    overScrollState: OverScrollState = LocalOverScrollState.current,
    maxOffset: Float = overScrollState.maxOffset,
    rotationMultiple: Float = 1f
) = this.then(
    OverscrollGraphicsLayerModifier(
        overScrollState = overScrollState,
        maxOffset = maxOffset,
        rotationMultiple = rotationMultiple,
        effect = OverScrollEffect.RotationCenter
    )
)

@Composable
fun Modifier.overScrollRotationVertical(
    overScrollState: OverScrollState = LocalOverScrollState.current,
    maxOffset: Float = overScrollState.maxOffset,
    rotationMultiple: Float = 1f
) = this.then(
    OverscrollGraphicsLayerModifier(
        overScrollState = overScrollState,
        maxOffset = maxOffset,
        rotationMultiple = rotationMultiple,
        effect = OverScrollEffect.RotationVertical
    )
)

@Composable
fun Modifier.overScrollRotationHorizontal(
    overScrollState: OverScrollState = LocalOverScrollState.current,
    maxOffset: Float = overScrollState.maxOffset,
    rotationMultiple: Float = 1f
) = this.then(
    OverscrollGraphicsLayerModifier(
        overScrollState = overScrollState,
        maxOffset = maxOffset,
        rotationMultiple = rotationMultiple,
        effect = OverScrollEffect.RotationHorizontal
    )
)

@Composable
fun Modifier.overScrollHeader(
    overScrollState: OverScrollState = LocalOverScrollState.current,
    headerHeight: Float = DefaultHeaderHeight,
) = this.then(
    OverscrollGraphicsLayerModifier(
        overScrollState = overScrollState,
        headerHeight = headerHeight,
        effect = OverScrollEffect.Header
    )
)