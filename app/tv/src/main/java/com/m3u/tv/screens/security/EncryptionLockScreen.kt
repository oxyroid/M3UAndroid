package com.m3u.tv.screens.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Usb
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Enhancement #3: Auto-Lock on USB Removal
 * Full-screen lock overlay when USB is removed
 */
@Composable
fun EncryptionLockScreen(
    lockReason: String,
    onUnlockAttempt: () -> Unit,
    onDisableEncryption: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(48.dp)
        ) {
            // Lock Icon
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Locked",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(80.dp)
            )

            // Title
            Text(
                text = "Application Locked",
                style = MaterialTheme.typography.displayMedium,
                color = Color.White
            )

            // Lock Reason
            Text(
                text = lockReason,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Instructions
            Text(
                text = "The application has been locked because the USB encryption key was removed.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f)
            )

            Text(
                text = "To unlock, please insert the USB device with the encryption key.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Unlock Button
            Button(
                onClick = onUnlockAttempt,
                colors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Usb,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Check USB and Unlock")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Disable Encryption Button (secondary action)
            Button(
                onClick = onDisableEncryption,
                colors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text("Disable Encryption (Requires USB)")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Warning: Disabling encryption will require the USB key to decrypt the database.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            )
        }
    }
}
