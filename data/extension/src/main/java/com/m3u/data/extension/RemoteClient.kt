package com.m3u.data.extension

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class RemoteClient {
    private var server: IRemoteService? = null
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(
            name: ComponentName?,
            service: IBinder?
        ) {
            server = IRemoteService.Stub.asInterface(service)
            _isConnectedObservable.value = true
            Log.d(TAG, "onServiceConnected, $name")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            server = null
            _isConnectedObservable.value = false
            Log.d(TAG, "onServiceDisconnected, $name")
        }

        override fun onBindingDied(name: ComponentName?) {
            super.onBindingDied(name)
            Log.e(TAG, "onBindingDied: $name")
        }

        override fun onNullBinding(name: ComponentName?) {
            super.onNullBinding(name)
            Log.e(TAG, "onNullBinding: $name")
        }
    }

    fun connect(
        context: Context,
        targetPackageName: String = PACKAGE_NAME_HOST
    ) {
        Log.d(TAG, "connect")
        val intent = Intent(context, RemoteServer::class.java).apply {
            action = "com.m3u.permission.CONNECT_EXTENSION_PLUGIN"
            component = ComponentName(targetPackageName, "com.m3u.data.extension.RemoteServer")
        }
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    suspend fun call(func: String, param: String): String? = suspendCoroutine { cont ->
        val remoteService = requireNotNull(server) { "RemoteService is not connected!" }
        remoteService.call(func, param, object : IRemoteCallback.Stub() {
            override fun onSuccess(func: String?, param: String?) {
                Log.d(TAG, "onSuccess: $func, $param")
                cont.resume(param)
            }

            override fun onError(
                func: String?,
                errorCode: String?,
                errorMessage: String?
            ) {
                Log.e(TAG, "onError: $func, $errorCode, $errorMessage")
                throw RuntimeException("Error: $func $param $errorCode, $errorMessage")
            }
        })
    }

    val isConnected: Boolean
        get() = server != null

    val isConnectedObservable: Flow<Boolean> get() = _isConnectedObservable
    private val _isConnectedObservable = MutableStateFlow(false)

    companion object {
        private const val TAG = "RemoteClient"
        const val PACKAGE_NAME_HOST = "com.m3u.smartphone"
    }
}