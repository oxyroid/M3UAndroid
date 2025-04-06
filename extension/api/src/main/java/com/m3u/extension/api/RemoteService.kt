package com.m3u.extension.api

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.m3u.data.extension.IRemoteCallback
import com.m3u.data.extension.IRemoteService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap

class RemoteService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val onRemoteCall: OnRemoteCall by lazy {
        ServiceLoader.load<OnRemoteCall>(
            OnRemoteCall::class.java,
            application.classLoader
        ).let {
            val count = it.count()
            if (count == 0) {
                throw IllegalStateException("No implementation of OnRemoteCall found")
            } else if (count > 1) {
                throw IllegalStateException("Multiple implementations of OnRemoteCall found")
            } else {
                it.first()
            }
        }.apply {
            setDependencies(dependencies)
        }
    }
    private val dependencies: RemoteServiceDependencies by lazy {
        ServiceLoader.load<RemoteServiceDependencies>(
            RemoteServiceDependencies::class.java,
            application.classLoader
        ).let {
            val count = it.count()
            if (count == 0) {
                throw IllegalStateException("No implementation of RemoteServiceDependencies found")
            } else if (count > 1) {
                throw IllegalStateException("Multiple implementations of RemoteServiceDependencies found")
            } else {
                it.first()
            }
        }
    }

    private val binders = ConcurrentHashMap<String, IRemoteService.Stub>()

    private inner class RemoteServiceImpl : IRemoteService.Stub() {
        override fun call(
            module: String,
            method: String,
            param: ByteArray,
            callback: IRemoteCallback?
        ) {
            scope.launch {
                onRemoteCall(module, method, param, callback)
                Log.d(TAG, "call: $module, $method")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind: $intent")
        intent ?: return null
        val packageName = intent.resolveActivity(application.packageManager).packageName
        val binder = binders.getOrPut(packageName) {
            RemoteServiceImpl()
        }
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind: $intent")
        intent ?: return super.onUnbind(intent)
        val packageName = intent.`package` ?: return super.onUnbind(intent)
        val binder = binders.remove(packageName)
        if (binder != null) {
            return true
        }
        return super.onUnbind(intent)
    }

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            "remote-service",
            "remote-service",
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = "Remote service is running"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: $intent, $flags, $startId")
        ServiceCompat.startForeground(
            this,
            startId,
            NotificationCompat.Builder(this, "remote_service")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentTitle("Remote Service")
                .setContentText("Remote service is running")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
        )
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        ServiceCompat.stopForeground(
            this,
            ServiceCompat.STOP_FOREGROUND_REMOVE
        )
        job.cancel()
    }

    companion object {
        private const val TAG = "RemoteClient"
    }
}