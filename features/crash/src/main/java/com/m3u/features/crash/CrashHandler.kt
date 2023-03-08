package com.m3u.features.crash

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
            val reportPending = PendingIntent.getActivity(
                context,
                192837,
                Intent(context, CrashActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
            )

            val alarmManager: AlarmManager =
                context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager[AlarmManager.ELAPSED_REALTIME_WAKEUP, 500] = reportPending
            exitProcess(2)
        }
    }
}