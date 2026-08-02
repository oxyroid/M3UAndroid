package com.m3u.smartphone.stability

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

internal enum class PreviousExitKind(val wireName: String) {
    ANR("anr"),
    NATIVE_CRASH("native_crash");

    companion object {
        fun fromReason(reason: Int): PreviousExitKind? = when (reason) {
            ApplicationExitInfo.REASON_ANR -> ANR
            ApplicationExitInfo.REASON_CRASH_NATIVE -> NATIVE_CRASH
            else -> null
        }
    }
}

/** Reports ANR/native process deaths on the next launch without reading system traces. */
internal object ProcessExitMonitor {
    private const val PREFERENCES_NAME = "stability-process-exit"
    private const val LAST_EXIT_TIMESTAMP = "last_exit_timestamp"

    fun reportPreviousExit(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        reportPreviousExitApi30(context)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun reportPreviousExitApi30(context: Context) {
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return
        val exits = activityManager.getHistoricalProcessExitReasons(context.packageName, 0, 20)
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (!preferences.contains(LAST_EXIT_TIMESTAMP)) {
            val baselineTimestamp = exits.maxOfOrNull(ApplicationExitInfo::getTimestamp)
                ?: System.currentTimeMillis()
            preferences.edit().putLong(LAST_EXIT_TIMESTAMP, baselineTimestamp).apply()
            return
        }
        if (exits.isEmpty()) return

        val newestTimestamp = exits.maxOf(ApplicationExitInfo::getTimestamp)
        val previousTimestamp = preferences.getLong(LAST_EXIT_TIMESTAMP, 0L)
        if (newestTimestamp <= previousTimestamp) return
        preferences.edit().putLong(LAST_EXIT_TIMESTAMP, newestTimestamp).apply()

        val latestRelevantExit = exits
            .asSequence()
            .filter { exit -> exit.timestamp > previousTimestamp }
            .mapNotNull { exit ->
                PreviousExitKind.fromReason(exit.reason)?.let { kind -> exit.timestamp to kind }
            }
            .maxByOrNull { (timestamp, _) -> timestamp }
            ?.second
            ?: return

        StabilityReporter.reportPreviousExit(latestRelevantExit)
    }
}
