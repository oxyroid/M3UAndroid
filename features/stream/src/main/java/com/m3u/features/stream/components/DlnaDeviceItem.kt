package com.m3u.features.stream.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import org.jupnp.model.meta.Device

@Immutable
internal data class DeviceWrapper(
    val device: Device<*, *, *>?
)

@Composable
internal fun rememberDeviceWrapper(device: Device<*, *, *>?): DeviceWrapper {
    return remember(device) {
        DeviceWrapper(device)
    }
}

@Composable
internal fun DlnaDeviceItem(
    deviceWrapper: DeviceWrapper,
    connected: Boolean,
    requestConnection: () -> Unit,
    loseConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actualOnClick by rememberUpdatedState(if (connected) loseConnection else requestConnection)

    ListItem(
        headlineContent = {
            Text(deviceWrapper.device?.displayString.orEmpty())
        },
        trailingContent = {
            Crossfade(connected, label = "icon") { connected ->
                Icon(
                    imageVector = if (connected) Icons.Rounded.CastConnected
                    else Icons.Rounded.Cast,
                    contentDescription = null,
                    tint = if (connected) MaterialTheme.colorScheme.primary
                    else LocalContentColor.current
                )
            }
        },
        modifier = modifier.clickable {
            actualOnClick()
        }
    )
}