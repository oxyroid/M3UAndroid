package com.m3u.data.security

import android.content.Context
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.Settings
import com.m3u.core.architecture.preferences.set
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enhancement #2: Key Verification on App Start
 * Manages encryption key fingerprinting and verification using HMAC-SHA256
 */
@Singleton
class KeyVerificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: Settings
) {
    private val timber = Timber.tag("KeyVerificationManager")

    companion object {
        // App-specific salt for HMAC
        private const val APP_SALT = "com.m3u.tv.encryption.v1"
        private const val HMAC_ALGORITHM = "HmacSHA256"
    }

    /**
     * Generate HMAC-SHA256 fingerprint of encryption key
     * @param key The encryption key to fingerprint
     * @return Hex-encoded fingerprint string
     */
    fun generateFingerprint(key: ByteArray): String {
        return try {
            val mac = Mac.getInstance(HMAC_ALGORITHM)
            val secretKey = SecretKeySpec(APP_SALT.toByteArray(), HMAC_ALGORITHM)
            mac.init(secretKey)
            val hmac = mac.doFinal(key)
            hmac.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            timber.e(e, "Failed to generate fingerprint")
            ""
        }
    }

    /**
     * Store key fingerprint securely in preferences
     * @param fingerprint The fingerprint to store
     */
    suspend fun storeFingerprint(fingerprint: String) {
        try {
            settings[PreferencesKeys.USB_ENCRYPTION_KEY_FINGERPRINT] = fingerprint
            settings[PreferencesKeys.USB_ENCRYPTION_LAST_VERIFIED] = System.currentTimeMillis()
            timber.d("Stored key fingerprint: ${fingerprint.takeLast(8)}")
        } catch (e: Exception) {
            timber.e(e, "Failed to store fingerprint")
        }
    }

    /**
     * Verify USB key matches stored fingerprint
     * @param key The key to verify
     * @return true if key matches stored fingerprint, false otherwise
     */
    suspend fun verifyKey(key: ByteArray): Boolean {
        return try {
            val storedFingerprint = settings.data.first()[PreferencesKeys.USB_ENCRYPTION_KEY_FINGERPRINT]
            if (storedFingerprint.isNullOrEmpty()) {
                timber.w("No stored fingerprint found")
                return false
            }

            val currentFingerprint = generateFingerprint(key)
            val matches = currentFingerprint == storedFingerprint

            if (matches) {
                // Update last verified timestamp
                settings[PreferencesKeys.USB_ENCRYPTION_LAST_VERIFIED] = System.currentTimeMillis()
                timber.d("Key verification successful")
            } else {
                timber.w("Key verification failed - fingerprint mismatch")
            }

            matches
        } catch (e: Exception) {
            timber.e(e, "Key verification error")
            false
        }
    }

    /**
     * Get last verification timestamp
     * @return Timestamp in milliseconds, or null if never verified
     */
    suspend fun getLastVerificationTime(): Long? {
        return try {
            val timestamp = settings.data.first()[PreferencesKeys.USB_ENCRYPTION_LAST_VERIFIED] ?: 0L
            if (timestamp > 0) timestamp else null
        } catch (e: Exception) {
            timber.e(e, "Failed to get last verification time")
            null
        }
    }

    /**
     * Clear stored fingerprint (when disabling encryption)
     */
    suspend fun clearFingerprint() {
        try {
            settings[PreferencesKeys.USB_ENCRYPTION_KEY_FINGERPRINT] = ""
            settings[PreferencesKeys.USB_ENCRYPTION_LAST_VERIFIED] = 0L
            timber.d("Cleared key fingerprint")
        } catch (e: Exception) {
            timber.e(e, "Failed to clear fingerprint")
        }
    }

    /**
     * Check if fingerprint exists (encryption initialized)
     * @return true if fingerprint is stored
     */
    suspend fun hasFingerprint(): Boolean {
        return try {
            val fingerprint = settings.data.first()[PreferencesKeys.USB_ENCRYPTION_KEY_FINGERPRINT]
            !fingerprint.isNullOrEmpty()
        } catch (e: Exception) {
            timber.e(e, "Failed to check fingerprint existence")
            false
        }
    }
}
