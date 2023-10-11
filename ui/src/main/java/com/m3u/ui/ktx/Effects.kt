package com.m3u.ui.ktx

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
import com.m3u.core.util.compose.ifUnspecified
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalTheme

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
    val actualColor = color.ifUnspecified { LocalTheme.current.primary }
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
