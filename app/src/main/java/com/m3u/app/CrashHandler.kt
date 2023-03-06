package com.m3u.app

import android.content.Context
import android.os.Looper
import com.m3u.core.architecture.Logger
import com.m3u.core.util.context.toast
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.system.exitProcess

class CrashHandler @Inject constructor(
    private val logger: Logger,
    @ApplicationContext private val context: Context
) : Thread.UncaughtExceptionHandler {
    private val handler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        if (handler != null) {
            logger.log(throwable)
            context.toast("Uncaught error occurred!")
            Looper.loop()
            exitProcess(1)
        }
    }
}