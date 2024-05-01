package com.m3u.material.texture

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun MeshContainer(
    modifier: Modifier = Modifier,
    color1: Color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
    color2: Color = MaterialTheme.colorScheme.background,
    colorAnimationSpec: InfiniteRepeatableSpec<Color> = infiniteRepeatable(
        animation = tween(4000),
        repeatMode = RepeatMode.Reverse
    ),
    content: @Composable () -> Unit
) {
    val transition = rememberInfiniteTransition("mesh-texture-container")
    val color by transition.animateColor(
        initialValue = color1,
        targetValue = color2,
        animationSpec = colorAnimationSpec,
        label = "mesh-textures-container-color"
    )
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(color2, color1, color2)
                )
            )
            .texture(
                texture = Texture.Mesh(
                    color = color,
                    width = 8f,
                    xSpacing = 72f,
                    xAdditional = 24f
                )
            ),
        content = { content() }
    )
}