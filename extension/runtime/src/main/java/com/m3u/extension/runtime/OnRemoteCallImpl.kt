package com.m3u.extension.runtime

import android.util.Log
import com.google.auto.service.AutoService
import com.m3u.data.extension.IRemoteCallback
import com.m3u.extension.api.Method
import com.m3u.extension.api.Module
import com.m3u.extension.api.OnRemoteCall
import com.m3u.extension.api.RemoteCallException
import com.m3u.extension.api.Samplings
import com.m3u.extension.api.Utils
import com.m3u.extension.api.Utils.getAdapter
import com.m3u.extension.runtime.business.InfoModule
import com.m3u.extension.runtime.business.RemoteModule
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.valueParameters

@AutoService(OnRemoteCall::class)
class OnRemoteCallImpl : OnRemoteCall {
    private val remoteModules: Map<String, RemoteModule> by lazy {
        Samplings.measure("modules") {
            listOf<RemoteModule>(
                InfoModule()
            )
                .associateBy {
                    checkNotNull(it::class.findAnnotation<Module>()) {
                        "${it::class.simpleName} must manually inherit his parent's annotations."
                    }.name
                }
        }
    }

    // Map<module-name, Map<method-name, method>>
    private val remoteMethods = mutableMapOf<String, Map<String, KFunction<*>>>()

    // Map<type-name, adapter>
    private val adapters = mutableMapOf<String, Any>()

    override suspend fun invoke(
        module: String,
        method: String,
        bytes: ByteArray,
        callback: IRemoteCallback?
    ) {
        try {
            val instance = remoteModules[module]
            if (instance == null) {
                callback?.onError(
                    module,
                    method,
                    OnRemoteCall.ERROR_CODE_MODULE_NOT_FOUNDED,
                    "Module \"$module\" not founded, available modules: ${remoteModules.keys}"
                )
                return
            }
            Samplings.measure("total-$module/$method") {
                invokeImpl(instance, module, method, bytes, callback)
            }
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
                OnRemoteCall.ERROR_CODE_UNCAUGHT,
                e.stackTraceToString()
            )
        } finally {
            Samplings.separate()
        }
    }

    private suspend fun invokeImpl(
        instance: RemoteModule,
        module: String,
        method: String,
        bytes: ByteArray,
        callback: IRemoteCallback?
    ) {
        val methods = remoteMethods.getOrPut(module) {
            Samplings.measure("methods") {
                val moduleClass = instance::class
                moduleClass.declaredFunctions
                    .filter { it.hasAnnotation<Method>() }
                    .associateBy { it.findAnnotation<Method>()!!.name }
            }
        }
        val remoteMethod = methods[method]
        if (remoteMethod == null) {
            callback?.onError(
                module,
                method,
                OnRemoteCall.ERROR_CODE_METHOD_NOT_FOUNDED,
                "Method \"$method\" not founded, available methods: ${methods.keys}"
            )
            return
        } else {
            Log.e(TAG, "invokeImpl: scanned methods: ${methods.keys}")
        }
        val args = remoteMethod.valueParameters.map { parameter ->
            try {
                val adapter = adapters.getOrPut(parameter.type.javaClass.typeName) {
                    getAdapter(parameter.type.javaClass.typeName)
                }
                Samplings.measure("decode") {
                    Utils.decode(adapter, bytes)
                }
            } catch (e: Exception) {
                throw UnsupportedOperationException(
                    "Unsupported parameter type: ${parameter.type}", e
                )
            }
        }
        try {
            val result = Samplings.measure("inner-$module/$method") {
                remoteMethod.callSuspend(instance, *args.toTypedArray())
            }
            val clazz = result!!::class.java
            val adapter = adapters.getOrPut(clazz.name) { getAdapter(clazz.name) }
            callback?.onSuccess(
                module,
                method,
                Utils.encode(adapter, result)
            )
        } catch (e: Exception) {
            callback?.onError(
                module,
                method,
                OnRemoteCall.ERROR_CODE_UNCAUGHT,
                e.stackTraceToString()
            )
        }
    }

    companion object {
        private const val TAG = "Host-OnRemoteCallImpl"
    }
}
