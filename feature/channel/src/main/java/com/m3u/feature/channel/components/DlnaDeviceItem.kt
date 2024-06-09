package com.m3u.feature.channel.components

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.m3u.material.components.Icon
import net.mm2d.upnp.Device

@Composable
internal fun DlnaDeviceItem(
    device: Device?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = { Text(device?.friendlyName.orEmpty()) },
        trailingContent = {
            Icon(
                imageVector = Icons.Rounded.Cast,
                contentDescription = null
            )
        },
        modifier = modifier.clickable(onClick = onClick)
    )
}