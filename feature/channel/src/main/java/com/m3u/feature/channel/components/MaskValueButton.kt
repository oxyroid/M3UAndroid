package com.m3u.feature.channel.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.IconButton
import com.m3u.material.components.mask.MaskState
import com.m3u.ui.FontFamilies

@Composable
fun MaskTextButton(
    state: MaskState,
    icon: ImageVector,
    text: String?,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tooltipState: TooltipState = rememberTooltipState(),
    tint: Color = Color.Unspecified,
    enabled: Boolean = true
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
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
        ) {
            if (text != null) {
                Text(
                    text = text,
                    color = tint,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamilies.JetbrainsMono
                )
            }
            IconButton(
                onClick = {
                    state.wake()
                    onClick()
                },
                enabled = enabled
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = tint
                )
            }
        }
    }
}