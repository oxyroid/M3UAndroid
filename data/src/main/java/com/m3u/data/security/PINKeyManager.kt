package com.m3u.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.edit
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.Settings
import com.m3u.core.architecture.preferences.get
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages PIN-based encryption using Android Keystore and PBKDF2 key derivation.
 *
 * Security features:
 * - 6-digit PIN requirement
 * - PBKDF2 with 100,000 iterations for key derivation
 * - Salt stored securely in encrypted preferences
 * - Derived key encrypted using Android Keystore master key
 * - AES-256-GCM encryption
 * - Hardware-backed security when available (TEE/Secure Element)
 */
@Singleton
class PINKeyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: Settings
) {
    private val timber = Timber.tag("PINKeyManager")

    companion object {
        private const val KEYSTORE_ALIAS = "m3u_pin_master_key"
        private const val PIN_LENGTH = 6
        private const val PBKDF2_ITERATIONS = 100_000
        private const val KEY_SIZE_BITS = 256
        private const val GCM_TAG_LENGTH = 128
    }

    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    // ========================================
    // SESSION-BASED UNLOCK STATE MANAGEMENT
    // ========================================

    /**
     * Represents the current unlock state of the database.
     * This is session-based and cleared when the app process terminates.
     */
    sealed class UnlockState {
        /** Database is locked - PIN required */
        object Locked : UnlockState()

        /** Database is unlocked - key available in memory */
        data class Unlocked(val timestamp: Long) : UnlockState()
    }

    /** Current unlock state (session-based, in-memory only) */
    private var unlockState: UnlockState = UnlockState.Locked

    /** Cached encryption key (cleared when locked) */
    private var cachedKey: ByteArray? = null

    /**
     * Validates that PIN is exactly 6 digits
     */
    fun isValidPIN(pin: String): Boolean {
        return pin.length == PIN_LENGTH && pin.all { it.isDigit() }
    }

    /**
     * Initializes encryption with a new PIN
     * Returns the 256-bit encryption key for SQLCipher
     */
    suspend fun initializeWithPIN(pin: String): Result<ByteArray> {
        return try {
            timber.d("=== Initializing encryption with PIN ===")

            if (!isValidPIN(pin)) {
                return Result.failure(IllegalArgumentException("PIN must be exactly $PIN_LENGTH digits"))
            }

            // Generate random salt for PBKDF2
            val salt = ByteArray(32)
            SecureRandom().nextBytes(salt)
            timber.d("Generated random salt")

            // Derive 256-bit key from PIN using PBKDF2
            val derivedKey = deriveKeyFromPIN(pin, salt)
            timber.d("Derived 256-bit key from PIN using PBKDF2")

            // Get or create Android Keystore master key
            val masterKey = getOrCreateMasterKey()
            timber.d("Retrieved Android Keystore master key")

            // Encrypt the derived key with master key
            val (encryptedKey, iv) = encryptWithMasterKey(derivedKey, masterKey)
            timber.d("Encrypted derived key with master key")

            // Store encrypted key, IV, and salt in preferences
            settings.edit { prefs ->
                prefs[PreferencesKeys.ENCRYPTED_DATABASE_KEY] = encryptedKey.toBase64()
                prefs[PreferencesKeys.ENCRYPTION_KEY_IV] = iv.toBase64()
                prefs[PreferencesKeys.ENCRYPTION_SALT] = salt.toBase64()
                prefs[PreferencesKeys.PIN_ENCRYPTION_ENABLED] = true
            }
            timber.d("Stored encrypted key material in preferences")

            timber.d("✓ PIN initialization complete")
            Result.success(derivedKey)
        } catch (e: Exception) {
            timber.e(e, "Failed to initialize PIN encryption")
            Result.failure(e)
        }
    }

    /**
     * Unlocks encryption by deriving key from PIN
     * Returns the 256-bit encryption key for SQLCipher
     */
    suspend fun unlockWithPIN(pin: String): Result<ByteArray> {
        return try {
            timber.d("=== Attempting to unlock with PIN ===")

            if (!isValidPIN(pin)) {
                return Result.failure(IllegalArgumentException("Invalid PIN format"))
            }

            // Retrieve stored salt
            val saltBase64 = settings.get(PreferencesKeys.ENCRYPTION_SALT) ?: ""
            if (saltBase64.isEmpty()) {
                return Result.failure(IllegalStateException("No encryption salt found"))
            }
            val salt = saltBase64.fromBase64()
            timber.d("Retrieved salt from preferences")

            // Derive key from entered PIN
            val derivedKey = deriveKeyFromPIN(pin, salt)
            timber.d("Derived key from entered PIN")

            // Retrieve encrypted key and IV
            val encryptedKeyBase64 = settings.get(PreferencesKeys.ENCRYPTED_DATABASE_KEY) ?: ""
            val ivBase64 = settings.get(PreferencesKeys.ENCRYPTION_KEY_IV) ?: ""

            if (encryptedKeyBase64.isEmpty() || ivBase64.isEmpty()) {
                return Result.failure(IllegalStateException("No encrypted key found"))
            }

            val encryptedKey = encryptedKeyBase64.fromBase64()
            val iv = ivBase64.fromBase64()
            timber.d("Retrieved encrypted key material")

            // Get master key from Keystore
            val masterKey = getOrCreateMasterKey()

            // Decrypt the stored key with master key
            val storedKey = decryptWithMasterKey(encryptedKey, iv, masterKey)
            timber.d("Decrypted stored key with master key")

            // Verify that derived key matches stored key
            if (derivedKey.contentEquals(storedKey)) {
                // Cache the key in memory for this session
                cachedKey = derivedKey.copyOf()
                unlockState = UnlockState.Unlocked(System.currentTimeMillis())

                timber.d("✓ PIN verification successful - database unlocked")
                timber.d("Key cached in memory for session")
                Result.success(derivedKey)
            } else {
                timber.w("✗ PIN verification failed - keys do not match")
                Result.failure(SecurityException("Incorrect PIN"))
            }
        } catch (e: Exception) {
            timber.e(e, "Failed to unlock with PIN")
            Result.failure(e)
        }
    }

    /**
     * Derives 256-bit key from PIN using PBKDF2-HMAC-SHA256
     */
    private fun deriveKeyFromPIN(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_SIZE_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    /**
     * Gets or creates the Android Keystore master key
     * This key is hardware-backed and cannot be extracted
     */
    private fun getOrCreateMasterKey(): SecretKey {
        return if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            timber.d("Using existing master key from Keystore")
            (keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        } else {
            timber.d("Creating new master key in Keystore")
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )

            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setUserAuthenticationRequired(false) // No biometric/lock screen required
                .build()

            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    }

    /**
     * Encrypts data using the Android Keystore master key
     * Returns (encrypted data, IV)
     */
    private fun encryptWithMasterKey(data: ByteArray, masterKey: SecretKey): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data)
        return Pair(encrypted, iv)
    }

    /**
     * Decrypts data using the Android Keystore master key
     */
    private fun decryptWithMasterKey(encryptedData: ByteArray, iv: ByteArray, masterKey: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, masterKey, spec)
        return cipher.doFinal(encryptedData)
    }

    /**
     * Clears all PIN encryption data
     */
    suspend fun clearPINEncryption() {
        try {
            timber.d("Clearing PIN encryption data")

            // Remove from preferences
            settings.edit { prefs ->
                prefs.remove(PreferencesKeys.ENCRYPTED_DATABASE_KEY)
                prefs.remove(PreferencesKeys.ENCRYPTION_KEY_IV)
                prefs.remove(PreferencesKeys.ENCRYPTION_SALT)
                prefs[PreferencesKeys.PIN_ENCRYPTION_ENABLED] = false
            }

            // Remove master key from Keystore
            if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
                keyStore.deleteEntry(KEYSTORE_ALIAS)
                timber.d("Deleted master key from Keystore")
            }

            timber.d("✓ PIN encryption data cleared")
        } catch (e: Exception) {
            timber.e(e, "Failed to clear PIN encryption")
        }
    }

    /**
     * Checks if PIN encryption is enabled
     */
    suspend fun isPINEncryptionEnabled(): Boolean {
        return try {
            settings.get(PreferencesKeys.PIN_ENCRYPTION_ENABLED) ?: false
        } catch (e: Exception) {
            timber.e(e, "Failed to check PIN encryption status")
            false
        }
    }

    // ========================================
    // SESSION STATE ACCESSORS
    // ========================================

    /**
     * Gets the encryption key if the database is currently unlocked.
     * Returns null if locked (PIN not entered yet).
     *
     * This is the key method that DatabaseModule uses to determine
     * if the database can be opened.
     */
    fun getEncryptionKeyIfUnlocked(): ByteArray? {
        return when (unlockState) {
            is UnlockState.Unlocked -> {
                timber.d("Returning cached encryption key (unlocked)")
                cachedKey?.copyOf()
            }
            is UnlockState.Locked -> {
                timber.d("Database is locked - no key available")
                null
            }
        }
    }

    /**
     * Locks the database by clearing the cached key.
     * User will need to enter PIN again to unlock.
     */
    fun lockDatabase() {
        timber.d("Locking database - clearing cached key")

        // Securely clear the key from memory
        cachedKey?.fill(0)
        cachedKey = null

        unlockState = UnlockState.Locked
        timber.d("✓ Database locked")
    }

    /**
     * Checks if the database is currently unlocked.
     */
    fun isUnlocked(): Boolean {
        return unlockState is UnlockState.Unlocked
    }

    // Base64 encoding/decoding helpers
    private fun ByteArray.toBase64(): String =
        android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)

    private fun String.fromBase64(): ByteArray =
        android.util.Base64.decode(this, android.util.Base64.NO_WRAP)
}
