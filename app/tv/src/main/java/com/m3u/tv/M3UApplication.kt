package com.m3u.tv

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.work.Configuration
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.get
import com.m3u.core.architecture.preferences.settings
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class M3UApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

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
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            performStartupVerification()
        }
    }

    private suspend fun performStartupVerification() {
        try {
            timber.d("=== STARTUP VERIFICATION ===")

            // Check if PIN encryption is enabled
            val pinEncryptionEnabled = settings[PreferencesKeys.PIN_ENCRYPTION_ENABLED] ?: false

            if (pinEncryptionEnabled) {
                timber.d("PIN encryption is enabled - PIN unlock screen will be shown by MainActivity")
            } else {
                timber.d("PIN encryption is not enabled")
            }

            timber.d("=== STARTUP VERIFICATION COMPLETE ===")
        } catch (e: Exception) {
            timber.e(e, "Error during startup verification")
        }
    }
}