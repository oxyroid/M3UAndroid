package com.m3u.features.live.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.m3u.features.live.R
import com.m3u.ui.components.AppDialog
import com.m3u.ui.components.CircularProgressIndicator
import com.m3u.ui.components.MaskState
import com.m3u.ui.components.OnDismiss
import com.m3u.ui.model.LocalSpacing
import net.mm2d.upnp.Device

@Composable
fun DlnaDeviceDialog(
    isDevicesVisible: Boolean,
    devices: List<Device>?,
    connectedLocations: List<String>,
    searching: Boolean,
    maskState: MaskState,
    onDismiss: OnDismiss,
    connectDlnaDevice: (location: String) -> Unit,
    disconnectDlnaDevice: (location: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    AppDialog(
        visible = isDevicesVisible,
        onDismiss = onDismiss,
        modifier = modifier
    ) {
        DisposableEffect(Unit) {
            maskState.lock()
            onDispose {
                maskState.unlock()
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                horizontal = spacing.medium,
                vertical = spacing.small
            )
        ) {
            Text(
                text = stringResource(R.string.dlna_devices),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.h6,
                modifier = Modifier.weight(1f)
            )
            AnimatedVisibility(
                visible = searching
            ) {
                CircularProgressIndicator()
            }
        }
        LazyColumn(
            modifier = Modifier.sizeIn(
                maxHeight = 320.dp
            )
        ) {
            items(devices ?: emptyList()) { device ->
                DlnaDeviceItem(
                    device = device,
                    connected = device.location in connectedLocations,
                    requestConnection = { connectDlnaDevice(device.location) },
                    loseConnection = { disconnectDlnaDevice(device.location) }
                )
            }
        }
    }
}