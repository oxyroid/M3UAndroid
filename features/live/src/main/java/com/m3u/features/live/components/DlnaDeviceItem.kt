package com.m3u.features.live.components

import androidx.compose.animation.Crossfade
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.IconButton
import androidx.compose.material.ListItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.m3u.ui.model.LocalTheme
import net.mm2d.upnp.Device

@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun DlnaDeviceItem(
    device: Device,
    connected: Boolean,
    requestConnection: () -> Unit,
    loseConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalTheme.current
    ListItem(
        text = {
            Text(device.friendlyName)
        },
        trailing = {
            val actualOnClick by rememberUpdatedState(if (connected) loseConnection else requestConnection)
            IconButton(
                onClick = actualOnClick
            ) {
                Crossfade(connected, label = "icon") { connected ->
                    Icon(
                        imageVector = if (connected) Icons.Rounded.CastConnected
                        else Icons.Rounded.Cast,
                        contentDescription = null,
                        tint = if (connected) theme.primary else Color.Unspecified
                    )
                }
            }
        }
    )
}