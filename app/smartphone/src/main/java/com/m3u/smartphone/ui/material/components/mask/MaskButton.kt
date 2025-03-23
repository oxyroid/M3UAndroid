package com.m3u.smartphone.ui.material.components.mask

import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.takeOrElse

@Composable
fun MaskButton(
    state: MaskState,
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tooltipState: TooltipState = rememberTooltipState(),
    tint: Color = Color.Unspecified,
    enabled: Boolean = true,
    wakeWhenClicked: Boolean = true
) {
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
            onClick = {
                if (wakeWhenClicked) state.wake()
                else state.sleep()
                onClick()
            },
            enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = tint.takeOrElse { LocalContentColor.current }
            ),
            modifier = modifier
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription
            )
        }
    }
}
