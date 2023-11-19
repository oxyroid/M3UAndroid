package com.m3u.material.ktx

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.m3u.material.model.LocalSpacing
import androidx.compose.material3.MaterialTheme

fun Modifier.interaction(
    type: InteractionType,
    source: InteractionSource,
    block: @Composable Modifier.(visible: Boolean) -> Modifier
): Modifier = composed { block(type.visibleIn(source)) }

fun Modifier.interactionBorder(
    type: InteractionType,
    source: InteractionSource,
    color: Color = Color.Unspecified,
    width: Dp = Dp.Unspecified,
    shape: Shape = RectangleShape
): Modifier = interaction(type, source) { visible ->
    val actualColor = color.ifUnspecified { MaterialTheme.colorScheme.primary }
    val actualDp = width.ifUnspecified { LocalSpacing.current.extraSmall }
    val currentColor by animateColor("BorderEffectColor") {
        if (visible) actualColor else Color.Transparent
    }
    val currentWidth by animateDp("BorderEffectWidth") {
        if (visible) actualDp else Dp.Hairline
    }
    border(
        width = currentWidth,
        color = currentColor,
        shape = shape
    )
}
