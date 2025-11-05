package com.m3u.data.repository.usbkey

import kotlinx.coroutines.flow.StateFlow

interface USBKeyRepository {
    val state: StateFlow<USBKeyState>

    /**
     * Initialize USB key encryption for the database
     * Creates encryption key file on USB stick
     */
    suspend fun initializeEncryption(): Result<Unit>

    /**
     * Disable encryption and decrypt the database
     */
    suspend fun disableEncryption(): Result<Unit>

    /**
     * Check if USB stick with valid key is connected
     */
    suspend fun validateUSBKey(): Result<Boolean>

    /**
     * Get encryption key from USB stick if available
     */
    suspend fun getEncryptionKey(): ByteArray?

    /**
     * Check if encryption is currently enabled
     */
    fun isEncryptionEnabled(): Boolean

    /**
     * Request USB permission from user
     */
    suspend fun requestUSBPermission(): Result<Unit>
}
