package com.m3u.data.extension

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap

class RemoteService : Service() {
    private val onRemoteCall: OnRemoteCall by lazy {
        ServiceLoader.load<OnRemoteCall>(
            OnRemoteCall::class.java
        ).let {
            val count = it.count()
            if (count == 0) {
                throw IllegalStateException("No implementation of OnRemoteCall found")
            } else if (count > 1) {
                throw IllegalStateException("Multiple implementations of OnRemoteCall found")
            } else {
                it.first()
            }
        }
    }

    private val binders = ConcurrentHashMap<String, IRemoteService.Stub>()

    private inner class RemoteServiceImpl: IRemoteService.Stub() {
        override fun call(
            module: String,
            method: String,
            param: ByteArray,
            callback: IRemoteCallback?
        ) {
            onRemoteCall(module, method, param, callback)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind: $intent")
        intent?: return null
        val packageName = intent.`package` ?: return null
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: $intent, $flags, $startId")
        return super.onStartCommand(intent, flags, startId)
    }

    companion object {
        private const val TAG = "RemoteClient"
    }
}