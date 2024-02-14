package com.m3u.androidApp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.pref.Pref
import com.m3u.dlna.DLNACastManager
import com.m3u.features.crash.CrashHandler
import com.m3u.material.ktx.isTelevision
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class M3UApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var handler: CrashHandler

    @Inject
    @Logger.MessageImpl
    lateinit var messager: Logger

    @Inject
    lateinit var pref: Pref

    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(handler)
        if (!resources.configuration.isTelevision() && !pref.alwaysTv) {
            DLNACastManager.bindCastService(this@M3UApplication)
        }
    }

    override val workManagerConfiguration: Configuration by lazy {
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }
}
