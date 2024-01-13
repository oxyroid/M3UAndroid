package com.m3u.material.components.mask

import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.m3u.material.components.IconButton

@Composable
fun MaskButton(
    state: MaskState,
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    enabled: Boolean = true
) {
    val tooltipState = rememberTooltipState()

    val currentTint by animateColorAsState(
        targetValue = tint,
        label = "mask-button-tint"
    )

    TooltipBox(
        state = tooltipState,
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(text = contentDescription.uppercase())
            }
        }
    ) {
        IconButton(
            icon = icon,
            enabled = enabled,
            contentDescription = contentDescription,
            onClick = {
                state.wake()
                onClick()
            },
            modifier = modifier,
            tint = currentTint
        )
    }
}
