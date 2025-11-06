package com.m3u.data.repository.encryption

import com.m3u.data.repository.usbkey.EncryptionProgress

interface PINEncryptionRepository {
    /**
     * Initialize encryption with a 6-digit PIN
     * @param pin Must be exactly 6 digits
     * @return Result containing Unit on success, Exception on failure
     */
    suspend fun initializeEncryption(pin: String): Result<Unit>

    /**
     * Unlock the encrypted database with PIN
     * @param pin The 6-digit PIN to unlock with
     * @return Result containing Unit on success, Exception on failure (wrong PIN or error)
     */
    suspend fun unlockWithPIN(pin: String): Result<Unit>

    /**
     * Disable encryption and decrypt the database
     * @param pin Current PIN for verification
     * @return Result containing Unit on success, Exception on failure
     */
    suspend fun disableEncryption(pin: String): Result<Unit>

    /**
     * Check if PIN encryption is currently enabled
     */
    suspend fun isEncryptionEnabled(): Boolean

    /**
     * Get the current encryption progress (if any operation is in progress)
     */
    suspend fun getEncryptionProgress(): EncryptionProgress?

    /**
     * Validate PIN format (6 digits)
     */
    fun isValidPIN(pin: String): Boolean
}
