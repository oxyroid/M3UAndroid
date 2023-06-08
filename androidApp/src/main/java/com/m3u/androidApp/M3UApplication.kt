package com.m3u.androidApp

import android.app.Application
import com.m3u.androidApp.koin.AppModule
import com.m3u.androidApp.koin.ViewModelModule
import com.m3u.core.koin.CoreModule
import com.m3u.data.koin.DataModule
import com.m3u.features.crash.CrashHandler
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class M3UApplication : Application() {
    private val handler: CrashHandler by inject()
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@M3UApplication)
            modules(
                AppModule,
                CoreModule.SharedPlatform,
                CoreModule.AndroidPlatform,
                DataModule.SharedPlatform,
                DataModule.AndroidPlatform,
                ViewModelModule
            )
        }
        Thread.setDefaultUncaughtExceptionHandler(handler)
    }
}
