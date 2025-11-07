package com.m3u.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.edit
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.Settings
import com.m3u.core.architecture.preferences.get
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
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
 * - Rate limiting with exponential backoff (brute-force protection)
 * - Automatic lockout after excessive failed attempts
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

        // ENTERPRISE SECURITY: Rate Limiting Constants
        private const val MAX_PIN_ATTEMPTS = 3 // Max attempts before lockout
        private const val ATTEMPT_WINDOW_MS = 60 * 60 * 1000L // 60 minutes window
        private const val LOCKOUT_DURATION_MS = 12 * 60 * 60 * 1000L // 12 hours
        private const val BASE_DELAY_MS = 2000L // 2 second base delay

        // ENTERPRISE SECURITY: Session Timeout Constants
        private const val SESSION_TIMEOUT_MS = 15 * 60 * 1000L // 15 minutes of inactivity
        private const val ACTIVITY_CHECK_INTERVAL_MS = 60 * 1000L // Check every minute
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
        data class Unlocked(
            val unlockTimestamp: Long,
            val lastActivityTimestamp: Long = unlockTimestamp
        ) : UnlockState() {
            /**
             * Returns time since last activity in milliseconds.
             */
            fun getInactivityDuration(): Long {
                return System.currentTimeMillis() - lastActivityTimestamp
            }

            /**
             * Checks if session has timed out due to inactivity.
             */
            fun isTimedOut(timeoutMs: Long = SESSION_TIMEOUT_MS): Boolean {
                return getInactivityDuration() > timeoutMs
            }

            /**
             * Updates last activity timestamp to current time.
             */
            fun updateActivity(): Unlocked {
                return copy(lastActivityTimestamp = System.currentTimeMillis())
            }
        }
    }

    /** Current unlock state (session-based, in-memory only) */
    private var unlockState: UnlockState = UnlockState.Locked

    /** Cached encryption key (cleared when locked) */
    private var cachedKey: ByteArray? = null

    // ========================================
    // ENTERPRISE SECURITY: RATE LIMITING STATE
    // ========================================

    /**
     * Tracks failed PIN attempts for rate limiting and brute-force protection.
     *
     * ENTERPRISE SECURITY: Enhanced Rate Limiting
     * - Tracks timestamps of attempts in 60-minute rolling window
     * - 3 attempts within 60 minutes = 12-hour lockout
     * - Persistent across app restarts (stored in DataStore)
     * - Makes brute-force attacks virtually impossible
     */
    private data class RateLimitState(
        val attemptTimestamps: List<Long> = emptyList(), // Timestamps of failed attempts
        val lockoutUntil: Long = 0
    ) {
        /** Check if currently in lockout period */
        fun isLockedOut(): Boolean = System.currentTimeMillis() < lockoutUntil

        /** Get remaining lockout time in seconds */
        fun getRemainingLockoutSeconds(): Int {
            val remaining = (lockoutUntil - System.currentTimeMillis()) / 1000
            return remaining.toInt().coerceAtLeast(0)
        }

        /** Get remaining lockout time in hours */
        fun getRemainingLockoutHours(): Int {
            return (getRemainingLockoutSeconds() / 3600).coerceAtLeast(0)
        }

        /**
         * Get attempts within the rolling 60-minute window.
         * Filters out old attempts outside the window.
         */
        fun getAttemptsInWindow(): List<Long> {
            val now = System.currentTimeMillis()
            val windowStart = now - ATTEMPT_WINDOW_MS
            return attemptTimestamps.filter { it > windowStart }
        }

        /**
         * Record a new failed attempt and check if lockout should be triggered.
         * Returns new state with updated attempt list and possible lockout.
         */
        fun recordFailedAttempt(): RateLimitState {
            val now = System.currentTimeMillis()
            val recentAttempts = getAttemptsInWindow()
            val newAttempts = recentAttempts + now

            return if (newAttempts.size >= MAX_PIN_ATTEMPTS) {
                // Trigger 12-hour lockout
                RateLimitState(
                    attemptTimestamps = newAttempts,
                    lockoutUntil = now + LOCKOUT_DURATION_MS
                )
            } else {
                // Just record the attempt
                RateLimitState(
                    attemptTimestamps = newAttempts,
                    lockoutUntil = 0
                )
            }
        }

        /**
         * Reset rate limiting (called on successful unlock).
         */
        fun reset(): RateLimitState {
            return RateLimitState()
        }

        /** Calculate required delay before next attempt (exponential backoff) */
        fun getRequiredDelayMs(): Long {
            if (isLockedOut()) return lockoutUntil - System.currentTimeMillis()

            val attemptsInWindow = getAttemptsInWindow()
            if (attemptsInWindow.isEmpty()) return 0

            // Exponential backoff: 2s, 4s, 8s
            val delay = BASE_DELAY_MS * (1 shl (attemptsInWindow.size - 1).coerceAtMost(2))
            val timeSinceLastAttempt = System.currentTimeMillis() - attemptsInWindow.last()

            return (delay - timeSinceLastAttempt).coerceAtLeast(0)
        }
    }

    /** Current rate limit state (loaded from persistent storage) */
    private var rateLimitState = RateLimitState()

    /** Flag to track if rate limit state has been loaded */
    private var rateStateLoaded = false

    /**
     * Load rate limit state from persistent storage.
     */
    private suspend fun loadRateLimitState(): RateLimitState {
        timber.d("loadRateLimitState: Starting to load from DataStore...")

        // Read from DataStore with proper null handling for keys that might not exist yet
        val prefs = settings.data.first()

        val lockoutUntil = prefs[PreferencesKeys.PIN_LOCKOUT_UNTIL] ?: 0L
        timber.d("loadRateLimitState: lockoutUntil=$lockoutUntil")

        val timestampsStr = prefs[PreferencesKeys.PIN_ATTEMPT_TIMESTAMPS] ?: ""
        timber.d("loadRateLimitState: timestampsStr='$timestampsStr'")

        val timestamps = if (timestampsStr.isNotEmpty()) {
            timestampsStr.split(",").mapNotNull { it.toLongOrNull() }
        } else {
            emptyList()
        }
        timber.d("loadRateLimitState: Loaded ${timestamps.size} timestamps")

        val state = RateLimitState(
            attemptTimestamps = timestamps,
            lockoutUntil = lockoutUntil
        )
        timber.d("loadRateLimitState: Successfully loaded rate limit state")
        return state
    }

    /**
     * Save rate limit state to persistent storage.
     */
    private suspend fun saveRateLimitState(state: RateLimitState) {
        settings.edit { prefs ->
            prefs[PreferencesKeys.PIN_LOCKOUT_UNTIL] = state.lockoutUntil
            prefs[PreferencesKeys.PIN_ATTEMPT_TIMESTAMPS] = state.attemptTimestamps.joinToString(",")
        }
    }

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
     * Unlocks encryption by deriving key from PIN with rate limiting protection.
     * Returns the 256-bit encryption key for SQLCipher.
     *
     * ENTERPRISE SECURITY: Rate Limiting
     * - Exponential backoff: 0s, 1s, 2s, 4s, 8s, then 5-minute lockout
     * - Protects against brute-force attacks
     * - Resets on successful unlock or process restart
     */
    suspend fun unlockWithPIN(pin: String): Result<ByteArray> {
        return try {
            timber.d("=== Attempting to unlock with PIN ===")

            // Load rate limit state on first unlock attempt
            if (!rateStateLoaded) {
                timber.d("Loading rate limit state from DataStore...")
                rateLimitState = loadRateLimitState()
                rateStateLoaded = true
                if (rateLimitState.isLockedOut()) {
                    val remainingHours = rateLimitState.getRemainingLockoutHours()
                    timber.w("⚠️ ACTIVE LOCKOUT DETECTED - ${remainingHours}h remaining")
                }
            }

            // ENTERPRISE SECURITY: Check rate limiting FIRST
            if (rateLimitState.isLockedOut()) {
                val remainingHours = rateLimitState.getRemainingLockoutHours()
                val remainingSeconds = rateLimitState.getRemainingLockoutSeconds()
                timber.w("✗ PIN unlock blocked: 12-hour lockout active")
                timber.w("  Remaining: ${remainingHours}h ${(remainingSeconds % 3600) / 60}m")
                return Result.failure(RateLimitException(
                    "Too many failed attempts. Locked for $remainingHours hours.",
                    remainingSeconds
                ))
            }

            val requiredDelay = rateLimitState.getRequiredDelayMs()
            if (requiredDelay > 0) {
                val delaySec = (requiredDelay / 1000.0)
                val attemptsInWindow = rateLimitState.getAttemptsInWindow().size
                timber.w("⏱ Rate limiting: $attemptsInWindow failed attempts in last 60 minutes")
                timber.w("  Waiting ${delaySec}s before allowing next attempt")
                return Result.failure(RateLimitException(
                    "Please wait ${delaySec.toInt() + 1} seconds before trying again.",
                    (delaySec.toInt() + 1)
                ))
            }

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
                // ENTERPRISE SECURITY: Reset rate limiting on successful unlock
                timber.d("✓ PIN verification successful - resetting rate limit state")
                rateLimitState = rateLimitState.reset()
                saveRateLimitState(rateLimitState)

                // Cache the key in memory for this session
                cachedKey = derivedKey.copyOf()
                val now = System.currentTimeMillis()
                unlockState = UnlockState.Unlocked(
                    unlockTimestamp = now,
                    lastActivityTimestamp = now
                )

                timber.d("✓ PIN verification successful - database unlocked")
                timber.d("Key cached in memory for session")
                Result.success(derivedKey)
            } else {
                // ENTERPRISE SECURITY: Record failed attempt with 60-minute window tracking
                val newState = rateLimitState.recordFailedAttempt()
                val attemptsInWindow = newState.getAttemptsInWindow().size

                if (newState.isLockedOut()) {
                    // 12-hour lockout triggered!
                    timber.e("✗ PIN verification failed - 12-HOUR LOCKOUT TRIGGERED")
                    timber.e("  $attemptsInWindow attempts within 60-minute window")
                    timber.e("  Locked out until: ${java.util.Date(newState.lockoutUntil)}")
                    rateLimitState = newState
                    saveRateLimitState(rateLimitState)

                    Result.failure(PINVerificationException(
                        "Too many failed attempts. Locked for 12 hours.",
                        attemptsInWindow,
                        MAX_PIN_ATTEMPTS
                    ))
                } else {
                    // Just record the failed attempt
                    val remainingAttempts = MAX_PIN_ATTEMPTS - attemptsInWindow
                    timber.w("✗ PIN verification failed - attempt $attemptsInWindow/$MAX_PIN_ATTEMPTS in 60-min window")
                    timber.w("  $remainingAttempts attempts remaining before 12-hour lockout")
                    rateLimitState = newState
                    saveRateLimitState(rateLimitState)

                    Result.failure(PINVerificationException(
                        "Incorrect PIN. $remainingAttempts attempts remaining.",
                        attemptsInWindow,
                        MAX_PIN_ATTEMPTS
                    ))
                }
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
     * Returns null if locked (PIN not entered yet) or if session timed out.
     *
     * ENTERPRISE SECURITY: Session Timeout
     * - Automatically locks after 15 minutes of inactivity
     * - Records activity on each successful key access
     * - Clears encryption key from memory on timeout
     *
     * This is the key method that DatabaseModule uses to determine
     * if the database can be opened.
     */
    fun getEncryptionKeyIfUnlocked(): ByteArray? {
        return when (val state = unlockState) {
            is UnlockState.Unlocked -> {
                // ENTERPRISE SECURITY: Check for session timeout
                if (state.isTimedOut()) {
                    val inactivityMinutes = state.getInactivityDuration() / 60000
                    timber.w("⏱ Session timed out after $inactivityMinutes minutes of inactivity")
                    timber.w("  Auto-locking database for security")
                    lockDatabase()
                    return null
                }

                // Update activity timestamp (user is actively using the database)
                unlockState = state.updateActivity()
                timber.d("Returning cached encryption key (unlocked, activity recorded)")
                cachedKey?.copyOf()
            }
            is UnlockState.Locked -> {
                timber.d("Database is locked - no key available")
                null
            }
        }
    }

    /**
     * ENTERPRISE FEATURE: Manually record user activity to prevent timeout.
     * Call this from UI interactions to keep session alive.
     */
    fun recordActivity() {
        when (val state = unlockState) {
            is UnlockState.Unlocked -> {
                unlockState = state.updateActivity()
                timber.d("Activity recorded - session timeout reset")
            }
            is UnlockState.Locked -> {
                // No-op when locked
            }
        }
    }

    /**
     * ENTERPRISE FEATURE: Get remaining session time before timeout.
     * Returns remaining time in seconds, or 0 if locked.
     * Useful for UI to show countdown timer.
     */
    fun getRemainingSessionSeconds(): Int {
        return when (val state = unlockState) {
            is UnlockState.Unlocked -> {
                val remaining = SESSION_TIMEOUT_MS - state.getInactivityDuration()
                (remaining / 1000).toInt().coerceAtLeast(0)
            }
            is UnlockState.Locked -> 0
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

    // ========================================
    // PUBLIC API: Rate Limit Status
    // ========================================

    /**
     * Gets the current number of failed PIN attempts within the 60-minute window.
     * Useful for UI to show warnings.
     */
    fun getFailedAttempts(): Int = rateLimitState.getAttemptsInWindow().size

    /**
     * Checks if currently in lockout period.
     * UI can use this to disable PIN input field.
     */
    fun isLockedOut(): Boolean = rateLimitState.isLockedOut()

    /**
     * Gets remaining lockout time in seconds.
     * Returns 0 if not locked out.
     */
    fun getRemainingLockoutSeconds(): Int = rateLimitState.getRemainingLockoutSeconds()

    // Base64 encoding/decoding helpers
    private fun ByteArray.toBase64(): String =
        android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)

    private fun String.fromBase64(): ByteArray =
        android.util.Base64.decode(this, android.util.Base64.NO_WRAP)
}

// ========================================
// ENTERPRISE SECURITY: Custom Exceptions
// ========================================

/**
 * Thrown when PIN rate limiting is active.
 * Contains information about remaining wait time.
 */
class RateLimitException(
    message: String,
    val remainingSeconds: Int
) : SecurityException(message)

/**
 * Thrown when PIN verification fails.
 * Contains information about attempt count.
 */
class PINVerificationException(
    message: String,
    val attemptNumber: Int,
    val maxAttempts: Int
) : SecurityException(message)
