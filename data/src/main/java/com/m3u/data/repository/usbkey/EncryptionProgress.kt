package com.m3u.data.repository.usbkey

import androidx.compose.runtime.Immutable

/**
 * Represents the progress of an encryption or decryption operation
 */
@Immutable
data class EncryptionProgress(
    val step: EncryptionStep,
    val percentage: Int,
    val estimatedTimeRemaining: Long? = null,
    val currentOperation: String
)

/**
 * The different steps involved in encryption/decryption
 */
enum class EncryptionStep {
    PREPARING,
    GENERATING_KEY,
    CREATING_DATABASE,
    MIGRATING_DATA,
    VERIFYING,
    FINALIZING,
    COMPLETE
}
