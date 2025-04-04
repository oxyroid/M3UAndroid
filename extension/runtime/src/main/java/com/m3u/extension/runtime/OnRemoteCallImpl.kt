package com.m3u.extension.runtime

import android.util.Log
import androidx.annotation.Keep
import com.google.auto.service.AutoService
import com.m3u.data.extension.IRemoteCallback
import com.m3u.extension.api.OnRemoteCall
import com.m3u.extension.api.RemoteCallException
import com.m3u.extension.api.Samplings
import com.squareup.wire.ProtoAdapter
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Proxy
import java.lang.reflect.Type
import java.util.ServiceLoader
import kotlin.reflect.KProperty1
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.declaredMembers

@AutoService(OnRemoteCall::class)
class OnRemoteCallImpl : OnRemoteCall {
    private val remoteModules by lazy {
        Samplings.measure("modules") {
            ServiceLoader.load(
                RemoteModule::class.java,
                OnRemoteCallImpl::class.java.classLoader
            )
                ?.toList().orEmpty().filterNotNull().associateBy { it.module }
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
        Log.d(TAG, "$module, $method, ${bytes.size}, $callback")
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
                    .onEach { Log.e(TAG, "invokeImpl: ${it.name}, ${it.annotations.toList()}", ) }
                    // FIXME: never reached
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
                parameter.type == RemoteModule.Continuation::class.java -> {
                    createContinuationArg(
                        module = module,
                        method = method,
                        param = parameter,
                        callback = callback
                    )
                }

                // FIXME: never reached
                parameter.isAnnotationPresent(RemoteMethodParam::class.java) -> {
                    val adapter = adapters.getOrPut(parameter.type.typeName) {
                        getAdapter(parameter.type.typeName)
                    }
                    Samplings.measure("decode") {
                        ProtoAdapter::class.java
                            .getDeclaredMethod("decode", ByteArray::class.java)
                            .invoke(adapter, bytes)
                    }
                }

                else -> throw UnsupportedOperationException("Unsupported parameter type: ${parameter.type}")
            }
        }
        try {
            Samplings.measure("inner-$module/$method") {
                remoteMethod.invoke(instance, *args.toTypedArray())
            }
        } catch (e: InvocationTargetException) {
            callback?.onError(
                module,
                method,
                OnRemoteCall.ERROR_CODE_UNCAUGHT,
                e.stackTraceToString()
            )
        }
    }

    private fun createContinuationArg(
        module: String,
        method: String,
        param: Parameter,
        callback: IRemoteCallback?
    ): RemoteModule.Continuation<*> {
        val typeName = parameterizedTypeNames.getOrPut(param) {
            (param.getRealParameterizedType() as Class<*>).name
        }
        val adapter = adapters.getOrPut(typeName) { getAdapter(typeName) }
        return Samplings.measure("continuation") {
            RemoteModule.Continuation.createProxy(
                onResume = { res ->
                    callback?.onSuccess(
                        module, method,
                        Samplings.measure("encode") {
                            val encodeMethod = ProtoAdapter::class.java.declaredMethods.first {
                                it.returnType == ByteArray::class.java
                            }
                            encodeMethod.invoke(adapter, res) as ByteArray?
                        }
                    )
                },
                onReject = { code, message ->
                    callback?.onError(
                        module,
                        method,
                        code,
                        message
                    )
                }
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getAdapter(typeName: String): Any = Samplings.measure("adapter") {
        val companionObject = Class.forName(typeName).kotlin.companionObject!!
        val property =
            companionObject.declaredMembers.first { it.name == "ADAPTER" } as KProperty1<Any, Any>
        property.get(companionObject)
    }

    private fun Parameter.getRealParameterizedType(): Type {
        return (parameterizedType as ParameterizedType).actualTypeArguments[0]
    }

    companion object {
        private const val TAG = "Host-OnRemoteCallImpl"
    }
}

interface RemoteModule {
    val module: String

    @Keep
    interface Continuation<R> {
        fun resume(result: R)
        fun reject(errorCode: Int, errorMessage: String)

        companion object {
            private const val TAG = "RemoteModule"

            @Suppress("UNCHECKED_CAST")
            internal fun createProxy(
                onResume: (Any) -> Unit,
                onReject: (Int, String) -> Unit
            ): Continuation<*> = Proxy.newProxyInstance(
                Continuation::class.java.classLoader,
                arrayOf(Continuation::class.java)
            ) { _, method, args ->
                Log.e(TAG, "createProxy: $args")
                when (method.name) {
                    "resume" -> onResume(args[0])
                    "reject" -> onReject(args[0] as Int, args[1] as String)
                    else -> {
                        // do nothing
                    }
                }
            } as Continuation<*>
        }
    }
}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class RemoteMethod(
    val name: String
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class RemoteMethodParam
