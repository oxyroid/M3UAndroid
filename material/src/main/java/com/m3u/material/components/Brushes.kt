@file:Suppress("unused")

package com.m3u.material.components

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import com.m3u.material.model.LocalTheme
import com.m3u.material.model.StepColor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Stable
@Composable
fun animatedRadialBrush(
    colors: ImmutableList<StepColor> = M3UBrushDefaults.createDefaultStepColors(),
    center: Offset = Offset.Unspecified,
    radius: Float = Float.POSITIVE_INFINITY,
    tileMode: TileMode = TileMode.Clamp,
    durationMillis: Int = M3UBrushDefaults.durationMillis,
    easing: Easing = LinearEasing,
    repeatMode: RepeatMode = RepeatMode.Reverse
): Brush {
    val transition = rememberInfiniteTransition()
    val animatedColors = transition.animateColors(
        colors = colors,
        durationMillis = durationMillis,
        easing = easing,
        repeatMode = repeatMode
    )
    return Brush.radialGradient(
        colors = animatedColors,
        center = center,
        radius = radius,
        tileMode = tileMode
    )
}

@Stable
@Composable
fun animatedLinearBrush(
    colors: ImmutableList<StepColor> = M3UBrushDefaults.createDefaultStepColors(),
    start: Offset = Offset.Zero,
    end: Offset = Offset.Infinite,
    tileMode: TileMode = TileMode.Clamp,
    durationMillis: Int = M3UBrushDefaults.durationMillis,
    easing: Easing = LinearEasing,
    repeatMode: RepeatMode = RepeatMode.Reverse
): Brush {
    val transition = rememberInfiniteTransition()
    val animatedColors = transition.animateColors(
        colors = colors,
        durationMillis = durationMillis,
        easing = easing,
        repeatMode = repeatMode
    )
    return Brush.linearGradient(
        colors = animatedColors,
        start = start,
        end = end,
        tileMode = tileMode
    )
}

@Stable
@Composable
fun animatedSweepBrush(
    colors: ImmutableList<StepColor> = M3UBrushDefaults.createDefaultStepColors(),
    center: Offset = Offset.Unspecified,
    durationMillis: Int = M3UBrushDefaults.durationMillis,
    easing: Easing = LinearEasing,
    repeatMode: RepeatMode = RepeatMode.Reverse
): Brush {
    val transition = rememberInfiniteTransition()
    val animatedColors = transition.animateColors(
        colors = colors,
        durationMillis = durationMillis,
        easing = easing,
        repeatMode = repeatMode
    )
    return Brush.sweepGradient(
        colors = animatedColors,
        center = center
    )
}

object M3UBrushDefaults {
    @Composable
    fun color1() = Color(0xff897fee)

    @Composable
    fun color2() = Color(0xffd859a9)

    @Composable
    fun color3() = LocalTheme.current.topBar

    @Composable
    fun createDefaultStepColors(
        colors: ImmutableList<Color> = persistentListOf(color1(), color2(), color3())
    ): ImmutableList<StepColor> = colors.map { StepColor(it, step) }.toImmutableList()

    @Composable
    fun createStepColors(
        colors: ImmutableList<Pair<Color, Int>>
    ): ImmutableList<StepColor> = colors.map { StepColor(it.first, it.second) }.toImmutableList()


    @Composable
    fun contentColor() = LocalTheme.current.onTopBar

    const val durationMillis: Int = 1600
    private const val step = 1
}

private fun List<StepColor>.stepColor(index: Int): StepColor {
    val currentStep = get(index).step
    val targetIndex = (index + currentStep) % size
    return get(targetIndex)
}

@Composable
private fun InfiniteTransition.animateColors(
    colors: ImmutableList<StepColor>,
    durationMillis: Int,
    easing: Easing,
    repeatMode: RepeatMode
): ImmutableList<Color> = colors
    .mapIndexed { index, stepColor ->
        animateColor(
            initialValue = stepColor.color,
            targetValue = colors.stepColor(index).color,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = durationMillis,
                    easing = easing
                ),
                repeatMode = repeatMode
            ),
            label = "${label}_$index"
        ).value
    }
    .toImmutableList()
