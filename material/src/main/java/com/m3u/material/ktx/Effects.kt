package com.m3u.material.ktx

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.takeOrElse
import com.m3u.material.model.LocalDuration
import com.m3u.material.model.LocalSpacing

@Composable
fun Modifier.interaction(
    type: InteractionType,
    source: InteractionSource,
    factory: @Composable Modifier.(visible: Boolean) -> Modifier
): Modifier = this then factory(type.visibleIn(source))

@Composable
fun Modifier.interactionBorder(
    type: InteractionType,
    source: InteractionSource,
    color: Color = Color.Unspecified,
    width: Dp = Dp.Unspecified,
    shape: Shape = RectangleShape
): Modifier = interaction(type, source) { visible ->
    val duration = LocalDuration.current
    val actualColor = color.takeOrElse { InteractionDefaults.BorderColor }
    val actualDp = width.takeOrElse { InteractionDefaults.BorderWidth }
    val currentColor by animateColorAsState(
        targetValue = if (visible) actualColor else Color.Transparent,
        animationSpec = tween(duration.medium),
        label = "interaction-border-color"
    )
    val currentWidth by animateDpAsState(
        targetValue = if (visible) actualDp else Dp.Hairline,
        animationSpec = tween(duration.medium),
        label = "interaction-border-width"
    )
    border(
        width = currentWidth,
        color = currentColor,
        shape = shape
    )
}

private object InteractionDefaults {
    val BorderColor @Composable get() = MaterialTheme.colorScheme.primary
    val BorderWidth @Composable get() = LocalSpacing.current.extraSmall
}
