package com.m3u.tv.screens.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.m3u.data.repository.usbkey.HealthStatus
import com.m3u.data.repository.usbkey.USBKeyState
import com.m3u.data.security.EncryptionMetricsCalculator
import kotlinx.coroutines.delay

/**
 * Enhancement #9: Encryption Status Dashboard
 * Comprehensive dashboard showing encryption system metrics
 */
@Composable
fun EncryptionStatusDashboard(
    usbKeyState: USBKeyState,
    metricsCalculator: EncryptionMetricsCalculator,
    modifier: Modifier = Modifier
) {
    var lastVerifiedTime by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(usbKeyState.lastVerificationTime) {
        while (true) {
            lastVerifiedTime = metricsCalculator.getLastVerifiedRelativeTime()
            delay(60000) // Update every minute
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Encryption Status",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Encryption Status Card
            StatusCard(
                title = "Encryption",
                value = if (usbKeyState.isEncryptionEnabled) "Enabled" else "Disabled",
                icon = Icons.Default.Security,
                status = if (usbKeyState.isEncryptionEnabled) HealthStatus.HEALTHY else HealthStatus.DISABLED,
                modifier = Modifier.weight(1f)
            )

            // USB Connection Card
            StatusCard(
                title = "USB Device",
                value = if (usbKeyState.isConnected) "Connected" else "Disconnected",
                icon = Icons.Default.Usb,
                status = when {
                    usbKeyState.isConnected -> HealthStatus.HEALTHY
                    usbKeyState.isEncryptionEnabled -> HealthStatus.WARNING
                    else -> HealthStatus.DISABLED
                },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Key Verification Card
            StatusCard(
                title = "Key Verification",
                value = when {
                    !usbKeyState.isEncryptionEnabled -> "N/A"
                    usbKeyState.keyVerified -> "Verified"
                    usbKeyState.verificationError != null -> "Failed"
                    else -> "Not Verified"
                },
                icon = Icons.Default.Verified,
                status = when {
                    !usbKeyState.isEncryptionEnabled -> HealthStatus.DISABLED
                    usbKeyState.keyVerified -> HealthStatus.HEALTHY
                    else -> HealthStatus.WARNING
                },
                modifier = Modifier.weight(1f)
            )

            // Database Size Card
            StatusCard(
                title = "Database Size",
                value = usbKeyState.databaseSize?.let {
                    metricsCalculator.formatBytes(it)
                } ?: "N/A",
                icon = Icons.Default.Storage,
                status = HealthStatus.DISABLED,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Last Verified Card
            StatusCard(
                title = "Last Verified",
                value = lastVerifiedTime ?: "Never",
                icon = Icons.Default.Check,
                status = HealthStatus.DISABLED,
                modifier = Modifier.weight(1f)
            )

            // Encryption Algorithm Card
            StatusCard(
                title = "Algorithm",
                value = if (usbKeyState.isEncryptionEnabled) {
                    "AES-256"
                } else {
                    "None"
                },
                icon = Icons.Default.Lock,
                status = HealthStatus.DISABLED,
                modifier = Modifier.weight(1f)
            )
        }

        // Overall Health Status
        Spacer(modifier = Modifier.height(8.dp))
        OverallHealthCard(
            healthStatus = usbKeyState.healthStatus,
            isLocked = usbKeyState.isLocked,
            lockReason = usbKeyState.lockReason
        )
    }
}

@Composable
private fun StatusCard(
    title: String,
    value: String,
    icon: ImageVector,
    status: HealthStatus,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, iconColor) = when (status) {
        HealthStatus.HEALTHY -> Pair(
            Color(0xFF4CAF50).copy(alpha = 0.1f),
            Color(0xFF4CAF50)
        )
        HealthStatus.WARNING -> Pair(
            Color(0xFFFFC107).copy(alpha = 0.1f),
            Color(0xFFFFC107)
        )
        HealthStatus.CRITICAL -> Pair(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.error
        )
        HealthStatus.DISABLED -> Pair(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Card(
        onClick = { /* Status card */ },
        modifier = modifier.height(100.dp),
        colors = CardDefaults.colors(
            containerColor = backgroundColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconColor,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    color = iconColor
                )
            }
        }
    }
}

@Composable
private fun OverallHealthCard(
    healthStatus: HealthStatus,
    isLocked: Boolean,
    lockReason: String?
) {
    val (title, icon, color) = when {
        isLocked -> Triple(
            "System Locked",
            Icons.Default.Lock,
            MaterialTheme.colorScheme.error
        )
        healthStatus == HealthStatus.HEALTHY -> Triple(
            "System Healthy",
            Icons.Default.Check,
            Color(0xFF4CAF50)
        )
        healthStatus == HealthStatus.WARNING -> Triple(
            "Warning",
            Icons.Default.Warning,
            Color(0xFFFFC107)
        )
        healthStatus == HealthStatus.CRITICAL -> Triple(
            "Critical Issue",
            Icons.Default.Close,
            MaterialTheme.colorScheme.error
        )
        else -> Triple(
            "Encryption Disabled",
            Icons.Default.Security,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Card(
        onClick = { /* Overall health */ },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.colors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(40.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = color
                )
                if (lockReason != null) {
                    Text(
                        text = lockReason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
