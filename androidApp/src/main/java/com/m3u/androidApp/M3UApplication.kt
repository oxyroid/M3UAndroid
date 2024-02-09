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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class M3UApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var handler: CrashHandler

    @Inject
    @Logger.Message
    lateinit var messager: Logger

    @Inject
    lateinit var pref: Pref

    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(handler)
        CoroutineScope(Dispatchers.IO).launch {
            if (resources.configuration.isTelevision() || pref.alwaysTv) {
//                tvRepository.startServer()
            } else {
                DLNACastManager.bindCastService(this@M3UApplication)
//                tvRepository
//                    .broadcast
//                    .onEach {
//                        messager.log(it, type = Message.TYPE_TELEVISION)
//                    }
//                    .launchIn(this)
            }
        }
    }

    override val workManagerConfiguration: Configuration by lazy {
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }
}
