package com.m3u.ui.components.basic

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.m3u.ui.local.LocalTheme

@Composable
@Stable
fun premiumBrush(
    color1: Color = PremiumBrushDefaults.color1(),
    color2: Color = PremiumBrushDefaults.color2(),
    color3: Color = PremiumBrushDefaults.color3(),
    center: Offset = Offset.Infinite,
    radius: Float = Float.POSITIVE_INFINITY,
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
    return Brush.radialGradient(
        colors = listOf(
            leftColor,
            rightColor,
            color3,
            color3,
        ),
        center = center,
        radius = radius
    )
}

object PremiumBrushDefaults {
    @Composable
    fun color1() = Color(0xff897fee)

    @Composable
    fun color2() = Color(0xffd859a9)

    @Composable
    fun color3() = LocalTheme.current.topBar

    @Composable
    fun contentColor() = LocalTheme.current.onTopBar

    const val durationMillis: Int = 1600
}