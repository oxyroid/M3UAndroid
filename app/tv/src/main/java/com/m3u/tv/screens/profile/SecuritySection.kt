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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.m3u.tv.screens.security.PINUnlockScreen
import com.m3u.tv.ui.components.EncryptionProgressDialog
import com.m3u.data.repository.usbkey.EncryptionProgress
import kotlinx.coroutines.delay
import timber.log.Timber

@Composable
internal fun SettingViewModel.SecuritySection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPINSetup by remember { mutableStateOf(false) }
    var showPINDisable by remember { mutableStateOf(false) }
    var pinEncryptionEnabled by remember { mutableStateOf(false) }
    var encryptionProgress by remember { mutableStateOf<EncryptionProgress?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var previousProgressStep by remember { mutableStateOf<com.m3u.data.repository.usbkey.EncryptionStep?>(null) }

    // Check PIN encryption status and progress
    LaunchedEffect(Unit) {
        pinEncryptionEnabled = isPINEncryptionEnabled()
        Timber.tag("SecuritySection").d("Initial encryption status: $pinEncryptionEnabled")

        // Poll for encryption progress
        while (true) {
            val currentProgress = getPINEncryptionProgress()
            encryptionProgress = currentProgress

            // Log progress changes
            if (currentProgress?.step != previousProgressStep) {
                Timber.tag("SecuritySection").d("Progress step changed: ${currentProgress?.step} - ${currentProgress?.percentage}% - ${currentProgress?.currentOperation}")
                previousProgressStep = currentProgress?.step
            }

            delay(100) // Update every 100ms
        }
    }

    // Update encryption status when progress completes
    LaunchedEffect(encryptionProgress) {
        if (encryptionProgress == null && previousProgressStep != null) {
            // Progress was running but now completed
            Timber.tag("SecuritySection").d("Progress completed, refreshing encryption status...")
            delay(500) // Small delay to ensure backend state is updated
            val newStatus = isPINEncryptionEnabled()
            Timber.tag("SecuritySection").d("New encryption status after completion: $newStatus")
            pinEncryptionEnabled = newStatus
            previousProgressStep = null
        }
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
                                // Show PIN input dialog for security confirmation
                                showPINDisable = true
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

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.feat_setting_pin_encryption_time_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 6-Hour Timer Explanation (when encryption is enabled)
                if (pinEncryptionEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "⏱️ Security Session Timer",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = "When PIN encryption is enabled, a 6-hour countdown timer appears in the top-right corner. This is a security feature that automatically shuts down the app after 6 hours to protect your data.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "• Timer starts when you enter your PIN\n• Displays remaining time (HH:MM:SS)\n• Changes color as time runs out\n• App closes completely at 00:00:00\n• Restart app and enter PIN to get a new 6-hour session",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.5f
                        )
                    }
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

        // Show PIN disable confirmation dialog (unlock screen - no confirmation needed)
        if (showPINDisable) {
            PINUnlockScreen(
                onPINEntered = { pin ->
                    Timber.tag("SecuritySection").d("=== PIN ENTERED FOR DISABLE: length=${pin.length} ===")
                    Timber.tag("SecuritySection").d("Calling disablePINEncryption()...")
                    disablePINEncryption(pin)
                    showPINDisable = false
                    Timber.tag("SecuritySection").d("PIN disable initiated")
                },
                onCancel = {
                    Timber.tag("SecuritySection").d("PIN disable cancelled")
                    showPINDisable = false
                }
            )
        }

        // Show encryption progress dialog during encrypt/decrypt operations
        EncryptionProgressDialog(
            progress = encryptionProgress
        )
    }
}
