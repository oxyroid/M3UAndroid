package com.m3u.extension.api

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.m3u.data.extension.IRemoteCallback
import com.m3u.data.extension.IRemoteService
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
        targetPackageName: String,
        targetClassName: String,
        targetPermission: String,
        accessKey: String
    ) {
        Log.d(TAG, "connect")
        val intent = Intent(context, RemoteService::class.java).apply {
            action = targetPermission
            component = ComponentName(targetPackageName, targetClassName)
            putExtra(Const.ACCESS_KEY, accessKey)
        }
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun disconnect(context: Context) {
        context.unbindService(connection)
        _isConnectedObservable.value = false
    }

    suspend fun call(
        module: String,
        method: String,
        param: ByteArray
    ): ByteArray = suspendCoroutine { cont ->
        val remoteService = requireNotNull(server) { "RemoteService is not connected!" }
        remoteService.call(module, method, param, object : IRemoteCallback.Stub() {
            override fun onSuccess(module: String, method: String, param: ByteArray) {
                Log.d(TAG, "onSuccess: $method, $param")
                cont.resume(param)
            }

            override fun onError(
                module: String,
                method: String,
                errorCode: Int,
                errorMessage: String?
            ) {
                Log.e(TAG, "onError: $method, $errorCode, $errorMessage")
                throw RuntimeException("Error: $method $errorCode, $errorMessage")
            }
        })
    }

    val isConnected: Boolean
        get() = server != null

    val isConnectedObservable: Flow<Boolean> get() = _isConnectedObservable
    private val _isConnectedObservable = MutableStateFlow(false)

    companion object {
        private const val TAG = "RemoteClient"
    }
}