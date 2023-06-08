package com.m3u.features.scheme

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.util.context.toast

class SchemeReceiverService : Service() {
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.data?.toString()
        applicationContext.toast(url.orEmpty())
        return START_NOT_STICKY
    }
}