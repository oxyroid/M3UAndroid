package com.m3u.data.service

import android.util.Log
import com.google.auto.service.AutoService
import com.m3u.data.extension.IRemoteCallback
import com.m3u.data.extension.OnRemoteServerCall
import com.m3u.data.extension.RemoteCallException
import java.util.ServiceLoader

@AutoService(OnRemoteServerCall::class)
class OnRemoteServiceCallImpl : OnRemoteServerCall {
    private val modules = ServiceLoader.load(RemoteModule::class.java)
        ?.toList().orEmpty().filterNotNull().associateBy { it.module }

    override fun onCall(module: String, method: String, param: String, callback: IRemoteCallback?) {
        Log.d(TAG, "onCall: $module, $method, $param, $callback")
        try {
            val moduleInstance = modules[module]
            if (moduleInstance == null) {
                callback?.onError(
                    module,
                    method,
                    OnRemoteServerCall.ERROR_CODE_MODULE_NOT_FOUNDED,
                    "Module $module not founded"
                )
                return
            }
            if (method !in moduleInstance.methods) {
                callback?.onError(
                    module,
                    method,
                    OnRemoteServerCall.ERROR_CODE_METHOD_NOT_FOUNDED,
                    "Method $method not founded"
                )
                return
            }
            moduleInstance.callMethod(method, param, callback)
        } catch (e: RemoteCallException) {
            callback?.onError(
                module,
                method,
                e.errorCode,
                e.errorMessage
            )
        } catch (e: Exception) {
            callback?.onError(
                module,
                method,
                OnRemoteServerCall.ERROR_CODE_UNCAUGHT,
                e.message
            )
        }
    }

    companion object {
        private const val TAG = "Host-OnRemoteServiceCallImpl"
    }
}

interface RemoteModule {
    val module: String
    val methods: List<String>
    fun callMethod(method: String, param: String, callback: IRemoteCallback?)
}