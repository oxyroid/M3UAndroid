package com.m3u.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.m3u.data.repository.usbkey.EncryptionProgress

/**
 * Enhancement #6: Encryption Progress Dialog
 * Shows real-time progress during encryption/decryption operations
 */
@Composable
fun EncryptionProgressDialog(
    progress: EncryptionProgress?,
    onDismiss: (() -> Unit)? = null
) {
    if (progress == null) return

    // Full screen overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            onClick = { /* Prevent dismiss */ },
            modifier = Modifier.padding(32.dp),
            colors = CardDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Text(
                    text = when (progress.step) {
                        com.m3u.data.repository.usbkey.EncryptionStep.PREPARING -> "Preparing..."
                        com.m3u.data.repository.usbkey.EncryptionStep.GENERATING_KEY -> "Generating Key..."
                        com.m3u.data.repository.usbkey.EncryptionStep.CREATING_DATABASE -> "Creating Database..."
                        com.m3u.data.repository.usbkey.EncryptionStep.MIGRATING_DATA -> "Migrating Data..."
                        com.m3u.data.repository.usbkey.EncryptionStep.VERIFYING -> "Verifying..."
                        com.m3u.data.repository.usbkey.EncryptionStep.FINALIZING -> "Finalizing..."
                        com.m3u.data.repository.usbkey.EncryptionStep.COMPLETE -> "Complete!"
                    },
                    style = MaterialTheme.typography.titleLarge
                )

                // Current operation description
                Text(
                    text = progress.currentOperation,
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Simple progress bar (box-based)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.percentage / 100f)
                            .height(8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                }

                // Percentage text
                Text(
                    text = "${progress.percentage}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Warning message
                if (progress.percentage < 100) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Please do not remove the USB device or close the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
