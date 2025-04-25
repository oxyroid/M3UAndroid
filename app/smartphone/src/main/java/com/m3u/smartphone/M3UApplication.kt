package com.m3u.smartphone

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.m3u.core.architecture.logger.Logger
import com.m3u.extension.runtime.Utils
import com.m3u.smartphone.ui.business.crash.CrashHandler
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

//    private val coroutineScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        Utils.init(this)
        Thread.setDefaultUncaughtExceptionHandler(handler)
//        ResponseBodies.WebPage
//            .onEach {
//            }
//            .launchIn(coroutineScope)
    }

    override val workManagerConfiguration: Configuration by lazy {
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }
}
