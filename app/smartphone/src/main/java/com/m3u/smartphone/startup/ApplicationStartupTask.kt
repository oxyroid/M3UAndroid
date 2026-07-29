package com.m3u.smartphone.startup

import androidx.work.WorkManager

fun interface ApplicationStartupTask {
    fun enqueue(workManager: WorkManager)
}
