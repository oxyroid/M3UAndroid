package com.m3u.extension.runtime

import com.google.auto.service.AutoService
import com.m3u.data.extension.IRemoteCallback
import com.m3u.extension.api.OnRemoteCall
import com.m3u.extension.api.RemoteCallException
import com.m3u.extension.api.Samplings
import com.m3u.extension.api.Utils
import com.m3u.extension.api.Utils.getAdapter
import com.m3u.extension.runtime.business.InfoModule
import java.lang.reflect.Method
import java.lang.reflect.Parameter

@AutoService(OnRemoteCall::class)
class OnRemoteCallImpl : OnRemoteCall {
    private val remoteModules by lazy {
        Samplings.measure("modules") {
            listOf(
                InfoModule()
            )
                .associateBy { it.module }
        }
    }

    // Map<module-name, Map<method-name, method>>
    private val remoteMethods = mutableMapOf<String, Map<String, Method>>()

    // Map<type-name, adapter>
    private val adapters = mutableMapOf<String, Any>()

    private val parameterizedTypeNames = mutableMapOf<Parameter, String>()

    override fun invoke(
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

    private fun invokeImpl(
        instance: RemoteModule,
        module: String,
        method: String,
        bytes: ByteArray,
        callback: IRemoteCallback?
    ) {
        val methods = remoteMethods.getOrPut(module) {
            Samplings.measure("methods") {
                val moduleClass = instance::class.java
                moduleClass.declaredMethods
                    .asSequence()
                    .filter { it.isAnnotationPresent(RemoteMethod::class.java) }
                    .associateBy { it.getAnnotation(RemoteMethod::class.java)!!.name }
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
        val args = remoteMethod.parameters.map { parameter ->
            when {
                parameter.isAnnotationPresent(RemoteMethodParam::class.java) -> {
                    val adapter = adapters.getOrPut(parameter.type.typeName) {
                        getAdapter(parameter.type.typeName)
                    }
                    Samplings.measure("decode") {
                        Utils.decode(adapter, bytes)
                    }
                }

                else -> throw UnsupportedOperationException("Unsupported parameter type: ${parameter.type}")
            }
        }
        try {
            val res = Samplings.measure("inner-$module/$method") {
                remoteMethod.invoke(instance, *args.toTypedArray())
            }
            val clazz = res::class.java
            val adapter = adapters.getOrPut(clazz.name) { getAdapter(clazz.name) }
            callback?.onSuccess(
                module,
                method,
                Utils.encode(adapter, res)
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

interface RemoteModule {
    val module: String
}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class RemoteMethod(
    val name: String
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class RemoteMethodParam
