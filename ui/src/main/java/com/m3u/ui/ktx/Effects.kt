package com.m3u.ui.ktx

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalTheme

enum class EffectType {
    PRESS, HOVER, DRAG, FOCUS
}

@Composable
private operator fun EffectType.contains(interactionSource: InteractionSource): Boolean {
    val pressed by interactionSource.collectIsPressedAsState()
    val dragged by interactionSource.collectIsDraggedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    return when (this) {
        EffectType.PRESS -> pressed
        EffectType.DRAG -> dragged
        EffectType.FOCUS -> focused
        EffectType.HOVER -> hovered
    }
}

fun Modifier.borderEffect(
    type: EffectType,
    source: InteractionSource,
    color: Color = Color.Unspecified,
    width: Dp = Dp.Unspecified,
    shape: Shape = RectangleShape
): Modifier = composed {
    val actualColor = color.takeIf { it.isSpecified } ?: LocalTheme.current.primary
    val actualDp = width.takeIf { it.isSpecified } ?: LocalSpacing.current.extraSmall

    val visible = source in type

    val currentColor by animateColor("EffectColor") {
        if (visible) actualColor else Color.Transparent
    }
    val currentWidth by animateDp("EffectWidth") {
        if (visible) actualDp else Dp.Hairline
    }
    border(
        width = currentWidth,
        color = currentColor,
        shape = shape
    )
}