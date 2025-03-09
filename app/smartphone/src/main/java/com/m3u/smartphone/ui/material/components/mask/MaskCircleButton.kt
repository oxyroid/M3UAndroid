package com.m3u.smartphone.ui.material.components.mask

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun MaskCircleButton(
    state: MaskState,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    enabled: Boolean = true,
    isSmallDimension: Boolean = false,
    interactionSource: MutableInteractionSource? = null
) {
    val dimension = if (isSmallDimension) 48.dp else 64.dp
    Surface(
        shape = CircleShape,
        enabled = enabled,
        onClick = {
            state.wake()
            onClick()
        },
        interactionSource = interactionSource,
        modifier = modifier,
        color = Color.Unspecified,
        contentColor = tint.takeOrElse { LocalContentColor.current }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(dimension)
        )
    }
}