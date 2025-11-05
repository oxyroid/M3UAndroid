package com.m3u.data.repository.usbkey

import androidx.compose.runtime.Immutable

@Immutable
data class USBKeyState(
    val isConnected: Boolean = false,
    val deviceName: String? = null,
    val isEncryptionEnabled: Boolean = false,
    val isDatabaseUnlocked: Boolean = false,
    val error: String? = null
)
