package com.m3u.features.live.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.m3u.material.components.CircularProgressIndicator
import com.m3u.material.components.MaskState
import com.m3u.material.components.OnDismiss
import com.m3u.material.model.LocalSpacing
import net.mm2d.upnp.Device
import com.m3u.i18n.R.string

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DlnaDevicesBottomSheet(
    isDevicesVisible: Boolean,
    devices: List<Device>?,
    connectedDevices: List<Device>,
    searching: Boolean,
    maskState: MaskState,
    onDismiss: OnDismiss,
    connectDlnaDevice: (device: Device) -> Unit,
    disconnectDlnaDevice: (device: Device) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val state = rememberModalBottomSheetState()
    if (isDevicesVisible) {
        ModalBottomSheet(
            sheetState = state,
            onDismissRequest = onDismiss,
            modifier = modifier,
            windowInsets = WindowInsets.systemBarsIgnoringVisibility.only(WindowInsetsSides.Top)
        ) {
            LaunchedEffect(devices, state.isVisible) {
                if (state.isVisible) state.expand()
            }
            LaunchedEffect(Unit) {
                maskState.sleep()
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(
                    horizontal = spacing.medium,
                    vertical = spacing.small
                )
            ) {
                Text(
                    text = stringResource(string.feat_live_dlna_devices),
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
                modifier = Modifier
                    .sizeIn(
                        maxHeight = 320.dp
                    )
                    .navigationBarsPadding()
            ) {
                items(devices ?: emptyList()) { device ->
                    DlnaDeviceItem(
                        device = device,
                        connected = device in connectedDevices,
                        requestConnection = { connectDlnaDevice(device) },
                        loseConnection = { disconnectDlnaDevice(device) }
                    )
                }
            }
        }
    }
}