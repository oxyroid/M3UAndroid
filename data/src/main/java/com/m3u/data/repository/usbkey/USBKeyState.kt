package com.m3u.data.repository.usbkey

import androidx.compose.runtime.Immutable

@Immutable
data class USBKeyState(
    val isConnected: Boolean = false,
    val deviceName: String? = null,
    val isEncryptionEnabled: Boolean = false,
    val isDatabaseUnlocked: Boolean = false,
    val error: String? = null,

    // Enhancement #2: Key Verification
    val keyVerified: Boolean = false,
    val lastVerificationTime: Long? = null,
    val verificationError: String? = null,

    // Enhancement #3: Auto-Lock
    val isLocked: Boolean = false,
    val lockReason: String? = null,

    // Enhancement #6: Encryption Progress
    val encryptionProgress: EncryptionProgress? = null,

    // Enhancement #9: Status Dashboard
    val databaseSize: Long? = null,
    val encryptionAlgorithm: String? = null,
    val autoLockEnabled: Boolean = true,
    val healthStatus: HealthStatus = HealthStatus.DISABLED
)
