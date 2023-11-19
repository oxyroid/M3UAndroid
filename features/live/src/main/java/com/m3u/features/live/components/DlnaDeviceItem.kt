package com.m3u.features.live.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.fourthline.cling.model.meta.Device
import androidx.compose.material3.MaterialTheme

@Composable
internal fun DlnaDeviceItem(
    device: Device<*, *, *>,
    connected: Boolean,
    requestConnection: () -> Unit,
    loseConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = MaterialTheme.colorScheme
    val actualOnClick by rememberUpdatedState(if (connected) loseConnection else requestConnection)

    ListItem(
        headlineContent = {
            Text(device.displayString)
        },
        trailingContent = {
            Crossfade(connected, label = "icon") { connected ->
                Icon(
                    imageVector = if (connected) Icons.Rounded.CastConnected
                    else Icons.Rounded.Cast,
                    contentDescription = null,
                    tint = if (connected) theme.primary else Color.Unspecified
                )
            }
        },
        modifier = modifier.clickable {
            actualOnClick()
        }
    )
}