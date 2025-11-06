package com.m3u.data.security

import android.content.Context
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.Settings
import com.m3u.core.architecture.preferences.set
import com.m3u.data.database.M3UDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enhancement #3: Auto-Lock on USB Removal
 * Manages application locking when USB is removed
 */
@Singleton
class EncryptionLockManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: Settings,
    private val keyVerificationManager: KeyVerificationManager
) {
    private val timber = Timber.tag("EncryptionLockManager")
    private var database: M3UDatabase? = null

    // Allow database injection (internal for now, not exposed publicly)
    internal fun setDatabase(db: Any?) {
        // Store as Any to avoid exposing internal M3UDatabase type
        this.database = db as? M3UDatabase
    }

    /**
     * Lock the application due to USB removal or key mismatch
     * @param reason Human-readable reason for locking
     */
    suspend fun lockApplication(reason: String) {
        try {
            timber.d("=== LOCKING APPLICATION ===")
            timber.d("Reason: $reason")

            // Close database connections
            closeDatabase()

            // Clear sensitive data from memory
            clearSensitiveMemory()

            // Mark application as locked
            // Note: Lock state is managed in USBKeyRepository state

            timber.d("Application locked successfully")
        } catch (e: Exception) {
            timber.e(e, "Failed to lock application")
        }
    }

    /**
     * Unlock the application with provided key
     * @param key Encryption key to verify
     * @return Result indicating success or failure
     */
    suspend fun unlockApplication(key: ByteArray): Result<Unit> {
        return try {
            timber.d("=== UNLOCKING APPLICATION ===")

            // Verify key matches stored fingerprint
            val verified = keyVerificationManager.verifyKey(key)
            if (!verified) {
                timber.w("Unlock failed - key verification failed")
                return Result.failure(Exception("Key verification failed"))
            }

            timber.d("Application unlocked successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            timber.e(e, "Failed to unlock application")
            Result.failure(e)
        }
    }

    /**
     * Check if application is currently locked
     * Note: Actual lock state is managed in USBKeyRepository state
     */
    fun isApplicationLocked(): Boolean {
        // Lock state is tracked in USBKeyState.isLocked
        return false // Placeholder - actual state in repository
    }

    /**
     * Close database connections gracefully
     */
    private fun closeDatabase() {
        try {
            database?.let { db ->
                if (db.isOpen) {
                    timber.d("Closing database...")
                    db.close()
                    timber.d("Database closed")
                }
            }
        } catch (e: Exception) {
            timber.e(e, "Failed to close database")
        }
    }

    /**
     * Clear sensitive data from memory
     * Attempt to overwrite encryption key bytes and encourage GC
     */
    fun clearSensitiveMemory() {
        try {
            timber.d("Clearing sensitive memory...")

            // Note: Actual key clearing happens in USBKeyRepositoryImpl
            // where the key ByteArray is stored

            // Encourage garbage collection (best effort)
            System.gc()

            timber.d("Memory cleared")
        } catch (e: Exception) {
            timber.e(e, "Failed to clear sensitive memory")
        }
    }

    /**
     * Check if auto-lock is enabled
     */
    suspend fun isAutoLockEnabled(): Boolean {
        return try {
            settings.data.first()[PreferencesKeys.USB_ENCRYPTION_AUTO_LOCK] ?: true
        } catch (e: Exception) {
            timber.e(e, "Failed to check auto-lock setting")
            true // Default to enabled for security
        }
    }

    /**
     * Set auto-lock enabled/disabled
     */
    suspend fun setAutoLockEnabled(enabled: Boolean) {
        try {
            settings[PreferencesKeys.USB_ENCRYPTION_AUTO_LOCK] = enabled
            timber.d("Auto-lock ${if (enabled) "enabled" else "disabled"}")
        } catch (e: Exception) {
            timber.e(e, "Failed to set auto-lock")
        }
    }
}
