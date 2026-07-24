package com.m3u.tv

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.m3u.data.worker.ExtensionPluginBootstrapWorker
import com.m3u.data.worker.ProviderCredentialRecoveryWorker
import com.m3u.data.worker.ProviderSessionCleanupWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class M3UApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        ProviderCredentialRecoveryWorker.enqueue(WorkManager.getInstance(this))
        ProviderSessionCleanupWorker.enqueue(
            workManager = WorkManager.getInstance(this),
        )
        ExtensionPluginBootstrapWorker.enqueue(WorkManager.getInstance(this))
    }

    override val workManagerConfiguration: Configuration by lazy {
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }
}
