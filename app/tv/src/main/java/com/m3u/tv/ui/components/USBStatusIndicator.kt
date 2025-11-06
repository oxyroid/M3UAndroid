package com.m3u.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import com.m3u.data.repository.usbkey.USBKeyState

/**
 * Enhancement #5: USB Status Indicator
 * Compact status indicator showing USB connection and encryption status
 */
@Composable
fun USBStatusIndicator(
    usbKeyState: USBKeyState,
    modifier: Modifier = Modifier
) {
    val (icon, tint, description) = when {
        // Locked state - red
        usbKeyState.isLocked -> Triple(
            Icons.Default.Lock,
            MaterialTheme.colorScheme.error,
            "Locked - USB removed"
        )

        // Connected and encrypted - green
        usbKeyState.isConnected && usbKeyState.isEncryptionEnabled && usbKeyState.isDatabaseUnlocked -> Triple(
            Icons.Default.LockOpen,
            Color(0xFF4CAF50), // Green
            "Encrypted and unlocked"
        )

        // Encryption enabled but not connected - yellow warning
        usbKeyState.isEncryptionEnabled && !usbKeyState.isConnected -> Triple(
            Icons.Default.Warning,
            Color(0xFFFFC107), // Amber/Yellow
            "USB disconnected"
        )

        // Connected but not encrypted - blue
        usbKeyState.isConnected && !usbKeyState.isEncryptionEnabled -> Triple(
            Icons.Default.Usb,
            MaterialTheme.colorScheme.primary,
            "USB connected"
        )

        // Not connected, not encrypted - gray
        else -> Triple(
            Icons.Default.Usb,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "No USB device"
        )
    }

    Box(
        modifier = modifier
            .size(40.dp)
            .background(
                color = tint.copy(alpha = 0.2f),
                shape = CircleShape
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}
