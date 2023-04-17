package com.m3u.androidApp

import android.app.Application
import com.m3u.features.crash.CrashHandler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class M3UApplication : Application() {
    @Inject
    lateinit var handler: CrashHandler
    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(handler)
    }
}
