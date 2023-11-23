package com.m3u.material.ktx

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
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
    val actualColor = color.ifUnspecified { InteractionDefaults.BorderColor }
    val actualDp = width.ifUnspecified { InteractionDefaults.BorderWidth }
    val currentColor by animateColor("interaction-border-color") {
        if (visible) actualColor else Color.Transparent
    }
    val currentWidth by animateDp("interaction-border-width") {
        if (visible) actualDp else Dp.Hairline
    }
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
