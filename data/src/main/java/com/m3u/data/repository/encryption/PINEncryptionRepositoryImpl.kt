package com.m3u.data.repository.encryption

import android.content.Context
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.Settings
import com.m3u.core.architecture.preferences.get
import com.m3u.core.architecture.preferences.set
import com.m3u.data.database.DatabaseMigrationHelper
import com.m3u.data.repository.usbkey.EncryptionProgress
import com.m3u.data.repository.usbkey.EncryptionStep
import com.m3u.data.security.PINKeyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class PINEncryptionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: Settings,
    private val pinKeyManager: PINKeyManager,
    private val migrationHelper: DatabaseMigrationHelper
) : PINEncryptionRepository {

    private val timber = Timber.tag("PINEncryptionRepository")

    private val _progress = MutableStateFlow<EncryptionProgress?>(null)
    override suspend fun getEncryptionProgress(): EncryptionProgress? = _progress.value

    override suspend fun initializeEncryption(pin: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            timber.d("=== initializeEncryption() STARTED ===")

            // Validate PIN format
            if (!pinKeyManager.isValidPIN(pin)) {
                return@withContext Result.failure(
                    IllegalArgumentException("PIN must be exactly 6 digits")
                )
            }

            updateProgress(EncryptionStep.PREPARING, 5, "Preparing encryption")

            // Initialize PIN and get derived encryption key
            updateProgress(EncryptionStep.VERIFYING, 10, "Generating encryption key")
            val keyResult = pinKeyManager.initializeWithPIN(pin)
            if (keyResult.isFailure) {
                clearProgress()
                return@withContext Result.failure(
                    keyResult.exceptionOrNull() ?: Exception("Failed to generate encryption key")
                )
            }

            val encryptionKey = keyResult.getOrNull()!!
            timber.d("Encryption key generated from PIN")

            // Check if database exists and needs encryption
            updateProgress(EncryptionStep.VERIFYING, 15, "Checking database status")
            val isDatabaseEncrypted = migrationHelper.isDatabaseEncrypted()
            timber.d("Database encryption status: $isDatabaseEncrypted")

            if (isDatabaseEncrypted == false) {
                // Database exists and is unencrypted - encrypt it
                timber.d("Database exists and is unencrypted - starting encryption migration")
                updateProgress(EncryptionStep.MIGRATING_DATA, 20, "Encrypting database")

                val migrationResult = migrationHelper.migrateToEncrypted(encryptionKey) { progress ->
                    // Map migration progress (0-100) to our overall progress (20-85)
                    val overallProgress = 20 + (progress * 0.65).toInt()
                    updateProgress(
                        EncryptionStep.MIGRATING_DATA,
                        overallProgress,
                        "Encrypting database: $progress%"
                    )
                }

                if (migrationResult.isFailure) {
                    clearProgress()
                    // Clean up PIN data if migration fails
                    pinKeyManager.clearPINEncryption()
                    return@withContext Result.failure(
                        Exception("Database encryption failed: ${migrationResult.exceptionOrNull()?.message}")
                    )
                }

                timber.d("✓ Database encrypted successfully")
                updateProgress(EncryptionStep.MIGRATING_DATA, 85, "Database encrypted")
            } else if (isDatabaseEncrypted == true) {
                // Database is already encrypted
                timber.d("Database is already encrypted")
                updateProgress(EncryptionStep.MIGRATING_DATA, 85, "Database already encrypted")
            } else {
                // No database exists yet - will be created encrypted on first use
                timber.d("No existing database found - will be created encrypted")
                updateProgress(EncryptionStep.MIGRATING_DATA, 85, "Ready for encrypted database")
            }

            // Mark encryption as enabled
            updateProgress(EncryptionStep.FINALIZING, 90, "Finalizing encryption setup")
            settings[PreferencesKeys.USB_ENCRYPTION_ENABLED] = true // Reusing this key for now
            settings[PreferencesKeys.USB_ENCRYPTION_IN_PROGRESS] = false

            // Clean up backup files
            migrationHelper.cleanupBackups()

            updateProgress(EncryptionStep.COMPLETE, 100, "Encryption enabled successfully")
            timber.d("=== Encryption initialization COMPLETE ===")

            // Clear progress after a delay
            kotlinx.coroutines.delay(1000)
            clearProgress()

            Result.success(Unit)
        } catch (e: Exception) {
            timber.e(e, "Failed to initialize encryption")
            clearProgress()
            // Clean up on failure
            pinKeyManager.clearPINEncryption()
            Result.failure(e)
        }
    }

    override suspend fun unlockWithPIN(pin: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            timber.d("=== unlockWithPIN() STARTED ===")

            // Validate PIN format
            if (!pinKeyManager.isValidPIN(pin)) {
                return@withContext Result.failure(
                    IllegalArgumentException("Invalid PIN format")
                )
            }

            // Attempt to unlock with PIN
            val unlockResult = pinKeyManager.unlockWithPIN(pin)
            if (unlockResult.isFailure) {
                timber.w("PIN unlock failed: ${unlockResult.exceptionOrNull()?.message}")
                return@withContext Result.failure(
                    unlockResult.exceptionOrNull() ?: SecurityException("Incorrect PIN")
                )
            }

            timber.d("✓ PIN verification successful - database unlocked")
            Result.success(Unit)
        } catch (e: Exception) {
            timber.e(e, "Failed to unlock with PIN")
            Result.failure(e)
        }
    }

    override suspend fun disableEncryption(pin: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            timber.d("=== disableEncryption() STARTED ===")

            // Verify PIN before disabling
            val verifyResult = pinKeyManager.unlockWithPIN(pin)
            if (verifyResult.isFailure) {
                return@withContext Result.failure(
                    SecurityException("Incorrect PIN - cannot disable encryption")
                )
            }

            updateProgress(EncryptionStep.PREPARING, 5, "Verifying PIN")
            val encryptionKey = verifyResult.getOrNull()!!

            // Check if database is encrypted and needs decryption
            updateProgress(EncryptionStep.VERIFYING, 10, "Checking database status")
            val isDatabaseEncrypted = migrationHelper.isDatabaseEncrypted()
            timber.d("Database encryption status: $isDatabaseEncrypted")

            if (isDatabaseEncrypted == true) {
                // Database is encrypted - decrypt it
                updateProgress(EncryptionStep.MIGRATING_DATA, 20, "Decrypting database")
                timber.d("Starting database decryption migration")

                val migrationResult = migrationHelper.migrateToUnencrypted(encryptionKey) { progress ->
                    // Map migration progress (0-100) to our overall progress (20-85)
                    val overallProgress = 20 + (progress * 0.65).toInt()
                    updateProgress(
                        EncryptionStep.MIGRATING_DATA,
                        overallProgress,
                        "Decrypting database: $progress%"
                    )
                }

                if (migrationResult.isFailure) {
                    clearProgress()
                    return@withContext Result.failure(
                        Exception("Database decryption failed: ${migrationResult.exceptionOrNull()?.message}")
                    )
                }

                timber.d("✓ Database decrypted successfully")
            } else {
                timber.d("Database is not encrypted or does not exist")
            }

            // Clear PIN encryption data
            updateProgress(EncryptionStep.FINALIZING, 90, "Removing encryption keys")
            pinKeyManager.clearPINEncryption()

            // Clear encryption settings
            settings[PreferencesKeys.USB_ENCRYPTION_ENABLED] = false
            settings[PreferencesKeys.USB_ENCRYPTION_IN_PROGRESS] = false

            // Clean up backup files
            migrationHelper.cleanupBackups()

            updateProgress(EncryptionStep.COMPLETE, 100, "Encryption disabled successfully")
            timber.d("=== Encryption disabled COMPLETE ===")

            // Clear progress after a delay
            kotlinx.coroutines.delay(1000)
            clearProgress()

            Result.success(Unit)
        } catch (e: Exception) {
            timber.e(e, "Failed to disable encryption")
            clearProgress()
            Result.failure(e)
        }
    }

    override suspend fun isEncryptionEnabled(): Boolean {
        return try {
            pinKeyManager.isPINEncryptionEnabled()
        } catch (e: Exception) {
            timber.e(e, "Failed to check encryption status")
            false
        }
    }

    override fun isValidPIN(pin: String): Boolean {
        return pinKeyManager.isValidPIN(pin)
    }

    private fun updateProgress(step: EncryptionStep, percentage: Int, operation: String) {
        timber.d("Progress: $step - $percentage% - $operation")
        _progress.value = EncryptionProgress(step, percentage, null, operation)
    }

    private fun clearProgress() {
        _progress.value = null
    }
}
