package com.m3u.tv

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.work.Configuration
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.get
import com.m3u.core.architecture.preferences.set
import com.m3u.core.architecture.preferences.settings
import com.m3u.data.repository.usbkey.USBKeyRepository
import com.m3u.data.security.KeyVerificationManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class M3UApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var usbKeyRepository: USBKeyRepository

    @Inject
    lateinit var keyVerificationManager: KeyVerificationManager

    private val timber = Timber.tag("M3UApplication")

    override val workManagerConfiguration: Configuration by lazy {
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        // TODO: REMOVE BEFORE PRODUCTION - Debug logging enabled
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.d("============================================")
            Timber.d("M3U TV APPLICATION STARTING (DEBUG MODE)")
            Timber.d("============================================")
        }

        // Launch startup verification asynchronously
        // Note: performStartupVerification() will check for pending encryption
        // and perform it BEFORE any database access if needed
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            performStartupVerification()
        }
    }

    private suspend fun performStartupVerification() {
        try {
            timber.d("=== STARTUP VERIFICATION ===")

            // Check if encryption is pending (app was killed to close database)
            val inProgress = settings[PreferencesKeys.USB_ENCRYPTION_IN_PROGRESS]
            val lastOperation = settings[PreferencesKeys.USB_ENCRYPTION_LAST_OPERATION] ?: ""

            if (inProgress == true && lastOperation == "ENCRYPTION_PENDING") {
                timber.d("!!! ENCRYPTION PENDING - Performing encryption with database closed !!!")

                // Now the database is CLOSED because the app was killed
                // We can safely perform the encryption
                val result = usbKeyRepository.performPendingEncryption()

                if (result.isSuccess) {
                    timber.d("✓ Encryption completed successfully!")
                    // Clear the pending flag
                    settings[PreferencesKeys.USB_ENCRYPTION_IN_PROGRESS] = false
                    settings[PreferencesKeys.USB_ENCRYPTION_LAST_OPERATION] = "ENCRYPTION_COMPLETED"
                    settings[PreferencesKeys.USB_ENCRYPTION_ENABLED] = true

                    timber.d("Restarting app to open encrypted database...")
                    // Restart ONE MORE TIME to open the encrypted database
                    android.os.Process.killProcess(android.os.Process.myPid())
                } else {
                    timber.e("✗ Encryption failed: ${result.exceptionOrNull()?.message}")
                    // Clear the pending flag so we don't retry
                    settings[PreferencesKeys.USB_ENCRYPTION_IN_PROGRESS] = false
                    settings[PreferencesKeys.USB_ENCRYPTION_LAST_OPERATION] = "ENCRYPTION_FAILED"
                }
                return // Don't continue with normal startup
            } else if (inProgress == true) {
                // Some other operation was in progress - just clear the flag
                timber.d("Clearing in-progress flag from previous operation: $lastOperation")
                settings[PreferencesKeys.USB_ENCRYPTION_IN_PROGRESS] = false
            }

            // Verify USB key on startup if encryption is enabled
            if (usbKeyRepository.isEncryptionEnabled()) {
                timber.d("Encryption is enabled - verifying USB key on startup...")

                usbKeyRepository.getEncryptionKey()?.let { key ->
                    timber.d("Found encryption key - verifying fingerprint...")
                    val verified = keyVerificationManager.verifyKey(key)

                    if (verified) {
                        timber.d("USB key verified successfully on startup")
                    } else {
                        timber.w("USB key verification failed on startup")
                    }
                } ?: run {
                    timber.w("No encryption key found on startup - USB may be disconnected")
                }
            } else {
                timber.d("Encryption is not enabled - skipping startup verification")
            }

            timber.d("=== STARTUP VERIFICATION COMPLETE ===")
        } catch (e: Exception) {
            timber.e(e, "Error during startup verification")
        }
    }
}