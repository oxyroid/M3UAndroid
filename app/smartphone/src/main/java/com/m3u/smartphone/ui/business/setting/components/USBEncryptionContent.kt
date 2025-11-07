package com.m3u.smartphone.ui.business.setting.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.m3u.business.setting.SettingProperties
import com.m3u.data.repository.usbkey.USBKeyState
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.material.model.LocalSpacing

@Composable
context(properties: SettingProperties)
internal fun USBEncryptionContent(
    usbKeyState: USBKeyState,
    onEnableEncryption: () -> Unit,
    onDisableEncryption: () -> Unit,
    onRequestUSBPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    var showEnableDialog by remember { mutableStateOf(false) }
    var showDisableDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        // Status Indicator
        Surface(
            shape = MaterialTheme.shapes.small,
            color = when {
                usbKeyState.error != null -> MaterialTheme.colorScheme.errorContainer
                usbKeyState.isDatabaseUnlocked -> MaterialTheme.colorScheme.primaryContainer
                usbKeyState.isEncryptionEnabled -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Circle,
                    contentDescription = null,
                    tint = when {
                        usbKeyState.error != null -> MaterialTheme.colorScheme.error
                        usbKeyState.isDatabaseUnlocked -> MaterialTheme.colorScheme.primary
                        usbKeyState.isEncryptionEnabled -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.outline
                    },
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = when {
                        usbKeyState.error != null ->
                            stringResource(string.feat_setting_usb_encryption_status_error, usbKeyState.error ?: "Unknown")
                        usbKeyState.isDatabaseUnlocked && usbKeyState.isEncryptionEnabled ->
                            stringResource(string.feat_setting_usb_encryption_status_unlocked)
                        usbKeyState.isEncryptionEnabled && !usbKeyState.isDatabaseUnlocked ->
                            stringResource(string.feat_setting_usb_encryption_status_locked)
                        else ->
                            stringResource(string.feat_setting_usb_encryption_status_disabled)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        usbKeyState.error != null -> MaterialTheme.colorScheme.onErrorContainer
                        usbKeyState.isDatabaseUnlocked -> MaterialTheme.colorScheme.onPrimaryContainer
                        usbKeyState.isEncryptionEnabled -> MaterialTheme.colorScheme.onTertiaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        // USB Connection Status
        AnimatedVisibility(visible = usbKeyState.isConnected) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.medium),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Usb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(string.feat_setting_usb_encryption_device_connected),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        usbKeyState.deviceName?.let { deviceName ->
                            Text(
                                text = deviceName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Control Buttons
        if (!usbKeyState.isEncryptionEnabled) {
            // Enable Encryption Button
            FilledTonalButton(
                onClick = {
                    if (usbKeyState.isConnected) {
                        showEnableDialog = true
                    } else {
                        onRequestUSBPermission()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null
                )
                Spacer(Modifier.width(spacing.small))
                Text(
                    text = if (usbKeyState.isConnected) {
                        stringResource(string.feat_setting_usb_encryption_enable)
                    } else {
                        stringResource(string.feat_setting_usb_encryption_connect_usb)
                    }
                )
            }
        } else {
            // Disable Encryption Button
            FilledTonalButton(
                onClick = { showDisableDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.LockOpen,
                    contentDescription = null
                )
                Spacer(Modifier.width(spacing.small))
                Text(
                    text = stringResource(string.feat_setting_usb_encryption_disable)
                )
            }
        }

        // Info Section
        OutlinedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(string.feat_setting_usb_encryption_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Enable Encryption Confirmation Dialog
    if (showEnableDialog) {
        AlertDialog(
            onDismissRequest = { showEnableDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null
                )
            },
            title = {
                Text(text = stringResource(string.feat_setting_usb_encryption_enable_title))
            },
            text = {
                Text(text = stringResource(string.feat_setting_usb_encryption_enable_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEnableDialog = false
                        onEnableEncryption()
                    }
                ) {
                    Text(text = stringResource(string.feat_setting_usb_encryption_enable_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEnableDialog = false }
                ) {
                    Text(text = stringResource(string.feat_setting_usb_encryption_cancel))
                }
            }
        )
    }

    // Disable Encryption Confirmation Dialog
    if (showDisableDialog) {
        AlertDialog(
            onDismissRequest = { showDisableDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null
                )
            },
            title = {
                Text(text = stringResource(string.feat_setting_usb_encryption_disable_title))
            },
            text = {
                Text(text = stringResource(string.feat_setting_usb_encryption_disable_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisableDialog = false
                        onDisableEncryption()
                    }
                ) {
                    Text(text = stringResource(string.feat_setting_usb_encryption_disable_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDisableDialog = false }
                ) {
                    Text(text = stringResource(string.feat_setting_usb_encryption_cancel))
                }
            }
        )
    }
}
