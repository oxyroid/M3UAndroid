package com.m3u.features.live.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.m3u.i18n.R.string
import com.m3u.material.components.CircularProgressIndicator
import com.m3u.material.components.MaskState
import com.m3u.material.components.OnDismiss
import com.m3u.material.model.LocalSpacing
import org.fourthline.cling.model.meta.Device

@Composable
fun DlnaDevicesBottomSheet(
    isDevicesVisible: Boolean,
    devices: List<Device<*, *, *>>?,
    connected: Device<*, *, *>?,
    searching: Boolean,
    maskState: MaskState,
    onDismiss: OnDismiss,
    connectDlnaDevice: (device: Device<*, *, *>) -> Unit,
    disconnectDlnaDevice: (device: Device<*, *, *>) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val state = rememberModalBottomSheetState()

    LaunchedEffect(isDevicesVisible) {
        if (isDevicesVisible) state.show()
        else state.hide()
    }
    if (isDevicesVisible) {
        ModalBottomSheet(
            sheetState = state,
            onDismissRequest = onDismiss,
            modifier = modifier,
            windowInsets = WindowInsets(0)
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
                    style = MaterialTheme.typography.titleMedium,
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
            ) {
                items(devices ?: emptyList()) { device ->
                    DlnaDeviceItem(
                        device = device,
                        connected = device == connected,
                        requestConnection = { connectDlnaDevice(device) },
                        loseConnection = { disconnectDlnaDevice(device) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(56.dp))
                }
            }
        }
    }
}