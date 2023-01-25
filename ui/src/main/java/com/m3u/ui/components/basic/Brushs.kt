package com.m3u.ui.components.basic

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
@Stable
fun premiumBrush(
    color1: Color = PremiumBrushDefaults.color1,
    color2: Color = PremiumBrushDefaults.color2,
    durationMillis: Int = PremiumBrushDefaults.durationMillis
): Brush {
    val transition = rememberInfiniteTransition()

    val leftColor by transition.animateColor(
        initialValue = color1,
        targetValue = color2,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val rightColor by transition.animateColor(
        initialValue = color2,
        targetValue = color1,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    return Brush.linearGradient(
        colors = listOf(leftColor, rightColor)
    )
}

internal object PremiumBrushDefaults {
    val color1 = Color(0xff897fee)
    val color2 = Color(0xffd859a9)
    const val durationMillis: Int = 1600
}