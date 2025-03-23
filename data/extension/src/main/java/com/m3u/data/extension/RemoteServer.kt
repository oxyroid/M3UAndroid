package com.m3u.data.extension

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.util.ServiceLoader

class RemoteServer : Service() {
    private val onRemoteServerCall: OnRemoteServerCall = ServiceLoader.load<OnRemoteServerCall>(
        OnRemoteServerCall::class.java
    ).let {
        val count = it.count()
        if (count == 0) {
            throw IllegalStateException("No implementation of OnRemoteServerCall found")
        } else if (count > 1) {
            throw IllegalStateException("Multiple implementations of OnRemoteServerCall found")
        } else {
            it.first()
        }
    }
    private val binder: IRemoteService.Stub = object : IRemoteService.Stub() {
        override fun call(
            func: String,
            param: String,
            callback: IRemoteCallback?
        ) {
            onRemoteServerCall.onCall(func, param, callback)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind: $intent")
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: $intent, $flags, $startId")
        return super.onStartCommand(intent, flags, startId)
    }

    companion object {
        private const val TAG = "RemoteClient"
    }
}