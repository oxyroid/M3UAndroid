package com.m3u.features.crash

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.m3u.core.architecture.FilePathCacher
import com.m3u.core.util.context.toast
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class CrashHandler @Inject constructor(
    private val cacher: FilePathCacher,
    @ApplicationContext private val context: Context
) : Thread.UncaughtExceptionHandler {
    private val handler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        if (handler != null) {
            cacher.write(throwable)
            context.toast("Uncaught error occurred!")
            throwable.printStackTrace()
            val pendingIntent = PendingIntent.getActivity(
                context,
                192837,
                Intent(context, CrashActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
            )
            val alarmManager: AlarmManager =
                context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager[AlarmManager.ELAPSED_REALTIME_WAKEUP, 2500] = pendingIntent
        }
    }
}