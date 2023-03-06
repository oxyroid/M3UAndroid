package com.m3u.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class M3UApplication @Inject constructor(
    private val handler: CrashHandler
): Application() {
    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(handler)
    }
}

