package com.m3u.tv.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.m3u.business.setting.SettingViewModel
import com.m3u.core.architecture.preferences.settings
import com.m3u.i18n.R
import com.m3u.tv.screens.security.PINInputScreen
import timber.log.Timber

@Composable
internal fun SettingViewModel.SecuritySection() {
    val context = LocalContext.current
    var showPINSetup by remember { mutableStateOf(false) }
    var pinEncryptionEnabled by remember { mutableStateOf(false) }

    // Check PIN encryption status
    LaunchedEffect(Unit) {
        pinEncryptionEnabled = isPINEncryptionEnabled()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // =========================
            // PIN ENCRYPTION SECTION
            // =========================
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // PIN Section Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.feat_setting_pin_encryption_group),
                        style = MaterialTheme.typography.headlineMedium
                    )

                    // PIN Status Icon
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = if (pinEncryptionEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // PIN Status Text
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = if (pinEncryptionEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column {
                        Text(
                            text = stringResource(R.string.feat_setting_pin_encryption_status_enabled.takeIf { pinEncryptionEnabled }
                                ?: R.string.feat_setting_pin_encryption_status_disabled),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = if (pinEncryptionEnabled)
                                "Database encrypted with 6-digit PIN"
                            else
                                "No PIN protection",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // PIN Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (!pinEncryptionEnabled) {
                        Button(
                            onClick = {
                                Timber.tag("SecuritySection").d("=== ENABLE PIN ENCRYPTION BUTTON CLICKED ===")
                                showPINSetup = true
                            }
                        ) {
                            Text(stringResource(R.string.feat_setting_pin_encryption_enable))
                        }
                    } else {
                        Button(
                            onClick = {
                                Timber.tag("SecuritySection").d("=== DISABLE PIN ENCRYPTION BUTTON CLICKED ===")
                                // TODO: Prompt for PIN confirmation before disabling
                                Timber.tag("SecuritySection").w("Disable not yet implemented - need PIN confirmation")
                            }
                        ) {
                            Text(stringResource(R.string.feat_setting_pin_encryption_disable))
                        }
                    }
                }

                // PIN Warning Text
                if (!pinEncryptionEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.feat_setting_pin_encryption_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

        }

        // Show PIN setup dialog
        if (showPINSetup) {
            PINInputScreen(
                title = stringResource(R.string.feat_setting_pin_encryption_setup_title),
                subtitle = stringResource(R.string.feat_setting_pin_encryption_setup_subtitle),
                onPINEntered = { pin ->
                    Timber.tag("SecuritySection").d("=== PIN ENTERED: length=${pin.length} ===")
                    Timber.tag("SecuritySection").d("Calling enablePINEncryption()...")
                    enablePINEncryption(pin)
                    showPINSetup = false
                    pinEncryptionEnabled = true
                    Timber.tag("SecuritySection").d("PIN setup complete")
                },
                onCancel = {
                    Timber.tag("SecuritySection").d("PIN setup cancelled")
                    showPINSetup = false
                }
            )
        }
    }
}
