package com.m3u.extension.api

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.m3u.data.extension.IRemoteCallback
import com.m3u.data.extension.IRemoteService
import com.m3u.extension.api.Utils.getAdapter
import com.m3u.extension.api.Utils.getRealParameterizedType
import com.m3u.extension.api.client.Method
import com.m3u.extension.api.client.Module
import com.squareup.wire.ProtoAdapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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
                cont.resumeWithException(RuntimeException("Error: $method $errorCode, $errorMessage"))
            }
        })
    }

    // Map<type-name, adapter>
    private val adapters = mutableMapOf<String, Any>()

    @Suppress("UNCHECKED_CAST")
    inline fun <reified P> create(): P {
        val clazz = P::class.java
        val moduleName = checkNotNull(clazz.getAnnotation<Module>(Module::class.java)) {
            "Module annotation not found"
        }.name
        return Proxy.newProxyInstance(
            clazz.classLoader,
            arrayOf(clazz)
        ) { _, method, args ->
            val methodName = checkNotNull(method.getAnnotation<Method>(Method::class.java)) {
                "Method annotation not found"
            }.name
            val parameters = method.parameters
            when {
                parameters.lastOrNull()?.type == Continuation::class.java -> {
                    val continuation = args.last() as Continuation<Any>
                    val block: suspend () -> Any = { // return type
                        val bytes = if (args.size == 1) {
                            ByteArray(0)
                        } else {
                            // param type
                            val adapter = getAdapter(args[0]::class.java.typeName) as ProtoAdapter<Any>
                            adapter.encode(args[0])
                        }
                        val returnType = (parameters.last().getRealParameterizedType() as Class<*>).name
                        val adapter = getAdapter(returnType) as ProtoAdapter<Any>
                        val response = call(moduleName, methodName, bytes)
                        adapter.decode(response)
                    }
                    (block as (Continuation<Any>) -> Any)(continuation) // R | COROUTINE_SUSPENDED
                }

                else -> throw UnsupportedOperationException("Unsupported method: $method")
            }
        } as P
    }

    val isConnected: Boolean
        get() = server != null

    val isConnectedObservable: Flow<Boolean> get() = _isConnectedObservable
    private val _isConnectedObservable = MutableStateFlow(false)

    companion object {
        const val TAG = "RemoteClient"
    }
}