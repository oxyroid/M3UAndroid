package com.m3u.tv.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.m3u.business.setting.SettingViewModel
import com.m3u.i18n.R

@Composable
internal fun SettingViewModel.SecuritySection() {
    val usbKeyState by usbKeyState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Text(
            text = stringResource(R.string.feat_setting_usb_encryption_group),
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        // USB Device Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Usb,
                contentDescription = null,
                tint = if (usbKeyState.isConnected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column {
                Text(
                    text = stringResource(R.string.feat_setting_usb_encryption_device),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = usbKeyState.deviceName ?: stringResource(R.string.feat_setting_usb_encryption_no_device),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Encryption Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.feat_setting_usb_encryption_status),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = when {
                        usbKeyState.isEncryptionEnabled && usbKeyState.isDatabaseUnlocked ->
                            stringResource(R.string.feat_setting_usb_encryption_status_unlocked)
                        usbKeyState.isEncryptionEnabled ->
                            stringResource(R.string.feat_setting_usb_encryption_status_locked)
                        else ->
                            stringResource(R.string.feat_setting_usb_encryption_status_disabled)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        usbKeyState.isEncryptionEnabled && usbKeyState.isDatabaseUnlocked ->
                            MaterialTheme.colorScheme.primary
                        usbKeyState.isEncryptionEnabled ->
                            MaterialTheme.colorScheme.error
                        else ->
                            MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!usbKeyState.isEncryptionEnabled) {
                Button(
                    onClick = { enableUSBEncryption() },
                    enabled = usbKeyState.isConnected
                ) {
                    Text(stringResource(R.string.feat_setting_usb_encryption_enable))
                }
            } else {
                Button(
                    onClick = { disableUSBEncryption() }
                ) {
                    Text(stringResource(R.string.feat_setting_usb_encryption_disable))
                }
            }

            if (!usbKeyState.isConnected) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { requestUSBPermission() }
                ) {
                    Text(stringResource(R.string.feat_setting_usb_encryption_request_permission))
                }
            }
        }

        // Warning Text
        if (!usbKeyState.isEncryptionEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.feat_setting_usb_encryption_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
