package com.m3u.extension.runtime

import com.google.auto.service.AutoService
import com.m3u.extension.api.IRemoteCallback
import com.m3u.extension.api.Method
import com.m3u.extension.api.Module
import com.m3u.extension.api.Samplings
import com.m3u.extension.runtime.business.InfoModule
import com.m3u.extension.runtime.business.RemoteModule
import com.m3u.extension.runtime.business.SubscribeModule
import com.squareup.wire.ProtoAdapter
import kotlin.reflect.KClass
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
                InfoModule(
                    modules = { remoteModules.keys.toList() },
                    methods = { module -> remoteMethods[module]?.map { it.key }.orEmpty() }
                ),
                SubscribeModule(dependencies)
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

    private val adapters = mutableMapOf<Class<*>, ProtoAdapter<Any>>()

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
        }
        val args = remoteMethod.valueParameters.map { parameter ->
            try {
                val type = (parameter.type.classifier as KClass<*>).java
                val adapter = adapters.getOrPut(type) {
                    ProtoAdapter.get(type) as ProtoAdapter<Any>
                }
                Samplings.measure("decode") {
                    adapter.decode(bytes)
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
            val bytes = if (result == null) ByteArray(0)
            else {
                val type = result::class.java
                val adapter = adapters.getOrPut(type) { ProtoAdapter.get(type) as ProtoAdapter<Any> }
                adapter.encode(result)
            }
            callback?.onSuccess(
                module,
                method,
                bytes
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
    private lateinit var dependencies: RemoteServiceDependencies

    override fun setDependencies(dependencies: RemoteServiceDependencies) {
        this.dependencies = dependencies
    }

    companion object {
        private const val TAG = "Host-OnRemoteCallImpl"
    }
}
