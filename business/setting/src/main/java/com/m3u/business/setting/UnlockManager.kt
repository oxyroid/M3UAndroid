package com.m3u.business.setting

import com.m3u.data.repository.encryption.PINEncryptionRepository
import com.m3u.data.security.PINKeyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages application-wide lock/unlock state for PIN encryption.
 *
 * This acts as an authentication gate - the app cannot access the database
 * until the user successfully unlocks with their PIN.
 *
 * Lifecycle:
 * 1. App starts → `initialize()` called
 * 2. Check if PIN encryption enabled → State becomes Locked or NoEncryption
 * 3. If Locked → Show PIN unlock screen
 * 4. User enters PIN → `attemptUnlock()` called
 * 5. On success → State becomes Unlocked → Main app can proceed
 */
@Singleton
class UnlockManager @Inject constructor(
    private val pinKeyManager: PINKeyManager,
    private val pinRepository: PINEncryptionRepository
) {
    private val timber = Timber.tag("UnlockManager")

    /**
     * Represents the current lock state of the application.
     */
    sealed class LockState {
        /** Checking encryption status */
        object Initializing : LockState()

        /** No encryption enabled - app can proceed normally */
        object NoEncryption : LockState()

        /** Database is locked - user must enter PIN */
        object Locked : LockState()

        /** Database is unlocked - app has full access */
        object Unlocked : LockState()

        /** An error occurred during initialization */
        data class Error(val message: String) : LockState()
    }

    private val _lockState = MutableStateFlow<LockState>(LockState.Initializing)

    /**
     * Observable lock state for UI
     */
    val lockState: StateFlow<LockState> = _lockState.asStateFlow()

    /**
     * Initialize the unlock manager by checking if PIN encryption is enabled.
     * Should be called in MainActivity.onCreate() BEFORE setContent.
     */
    suspend fun initialize() {
        try {
            timber.d("=== INITIALIZING UNLOCK MANAGER ===")
            _lockState.value = LockState.Initializing

            // Check if PIN encryption is enabled
            val encryptionEnabled = pinRepository.isEncryptionEnabled()
            timber.d("PIN encryption enabled: $encryptionEnabled")

            if (!encryptionEnabled) {
                timber.d("No encryption - proceeding to normal startup")
                _lockState.value = LockState.NoEncryption
            } else {
                timber.d("Encryption enabled - database is locked")
                _lockState.value = LockState.Locked
            }

            timber.d("=== UNLOCK MANAGER INITIALIZED ===")
        } catch (e: Exception) {
            timber.e(e, "Failed to initialize unlock manager")
            _lockState.value = LockState.Error("Failed to check encryption status: ${e.message}")
        }
    }

    /**
     * Attempts to unlock the database with the provided PIN.
     *
     * On success:
     * - The encryption key is cached in memory by PINKeyManager
     * - State changes to Unlocked
     * - Database can now be accessed
     *
     * On failure:
     * - State remains Locked
     * - User can try again
     *
     * @param pin The 6-digit PIN to verify
     * @return Result.success if PIN is correct, Result.failure otherwise
     */
    suspend fun attemptUnlock(pin: String): Result<Unit> {
        return try {
            timber.d("=== ATTEMPTING UNLOCK ===")
            timber.d("PIN length: ${pin.length}")

            // Verify PIN and derive encryption key
            val result = pinRepository.unlockWithPIN(pin)

            if (result.isSuccess) {
                timber.d("✓ PIN correct - unlocking database")
                _lockState.value = LockState.Unlocked
                Result.success(Unit)
            } else {
                timber.w("✗ PIN incorrect")
                // State remains Locked
                Result.failure(result.exceptionOrNull() ?: SecurityException("Unlock failed"))
            }
        } catch (e: Exception) {
            timber.e(e, "Error during unlock attempt")
            Result.failure(e)
        }
    }

    /**
     * Locks the database and clears the cached encryption key.
     * User will need to enter PIN again.
     *
     * This is useful for:
     * - Manual lock feature
     * - Auto-lock after timeout
     * - Security-sensitive operations
     */
    fun lock() {
        timber.d("=== LOCKING DATABASE ===")
        pinKeyManager.lockDatabase()
        _lockState.value = LockState.Locked
        timber.d("✓ Database locked")
    }

    /**
     * Checks if the database is currently unlocked.
     */
    fun isUnlocked(): Boolean {
        return _lockState.value is LockState.Unlocked
    }

    /**
     * Checks if the database is locked and requires PIN.
     */
    fun isLocked(): Boolean {
        return _lockState.value is LockState.Locked
    }
}
