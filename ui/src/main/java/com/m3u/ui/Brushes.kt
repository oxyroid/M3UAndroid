package com.m3u.ui

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun Brush.Companion.createPremiumBrush(
    color1: Color = MaterialTheme.colorScheme.primaryContainer,
    color2: Color = MaterialTheme.colorScheme.secondaryContainer
): Brush {
    val transition = rememberInfiniteTransition("premium-brush")

    val leftColor by transition.animateColor(
        initialValue = color1,
        targetValue = color2,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "left"
    )
    val rightColor by transition.animateColor(
        initialValue = color2,
        targetValue = color1,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "right"
    )
    return Brush.linearGradient(
        colors = listOf(leftColor, rightColor)
    )
}