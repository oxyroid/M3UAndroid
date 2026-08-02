package com.m3u.smartphone

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import coil.Coil
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.m3u.data.extension.artwork.ProviderArtworkFetcherFactory
import com.m3u.data.worker.ExtensionPluginBootstrapWorker
import com.m3u.data.worker.PersistedUriPermissionCleanupWorker
import com.m3u.data.worker.ProviderCredentialRecoveryWorker
import com.m3u.data.worker.ProviderSessionCleanupWorker
import com.m3u.data.worker.initializePersistedUriPermissionLeases
import com.m3u.i18n.R.string
import com.m3u.smartphone.stability.CrashFallbackCopy
import com.m3u.smartphone.stability.ProcessExitMonitor
import com.m3u.smartphone.stability.StabilityReporter
import com.m3u.smartphone.stability.configureCrashReporting
import com.m3u.smartphone.startup.ApplicationStartupTask
import dagger.hilt.android.HiltAndroidApp
import org.acra.ACRA
import org.acra.ktx.initAcra
import timber.log.Timber
import timber.log.Timber.DebugTree
import javax.inject.Inject

@HiltAndroidApp
class M3UApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var startupTasks: Set<@JvmSuppressWildcards ApplicationStartupTask>

    @Inject
    lateinit var providerArtworkFetcherFactory: ProviderArtworkFetcherFactory

    override fun onCreate() {
        super.onCreate()
        if (ACRA.isACRASenderServiceProcess()) return

        StabilityReporter.initialize(BuildConfig.BUILD_TYPE)
        if (BuildConfig.CRASH_REPORT_ENDPOINT.isNotBlank()) {
            ProcessExitMonitor.reportPreviousExit(this)
        }
        Coil.setImageLoader(
            ImageLoaderFactory {
                ImageLoader.Builder(this)
                    .components {
                        add(providerArtworkFetcherFactory)
                    }
                    .build()
            }
        )
        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        }
        initializePersistedUriPermissionLeases(this)
        val workManager = WorkManager.getInstance(this)
        PersistedUriPermissionCleanupWorker.enqueueRecovery(
            workManager
        )
        ProviderCredentialRecoveryWorker.enqueue(workManager)
        ProviderSessionCleanupWorker.enqueue(
            workManager = workManager,
        )
        ExtensionPluginBootstrapWorker.enqueue(workManager)
        startupTasks.forEach { task -> task.enqueue(workManager) }
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        initAcra {
            configureCrashReporting(
                endpoint = BuildConfig.CRASH_REPORT_ENDPOINT,
                fallbackCopy = CrashFallbackCopy(
                    notificationTitle = getString(string.crash_notification_title),
                    notificationText = getString(string.crash_notification_text),
                    notificationChannelName = getString(string.crash_notification_channel_name),
                    mailTo = "oxyroid@outlook.com",
                ),
            )
        }
    }

    override val workManagerConfiguration: Configuration by lazy {
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }
}
