package com.m3u.extension.runtime

import android.util.Log
import com.google.auto.service.AutoService
import com.m3u.data.extension.IRemoteCallback
import com.m3u.data.extension.OnRemoteCall
import com.m3u.data.extension.RemoteCallException
import com.squareup.wire.ProtoAdapter
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.util.ServiceLoader
import kotlin.collections.orEmpty
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.declaredMemberProperties

@AutoService(OnRemoteCall::class)
class OnRemoteCallImpl : OnRemoteCall {
    private val remoteModules = ServiceLoader.load(RemoteModule::class.java)
        ?.toList().orEmpty().filterNotNull().associateBy { it.module }

    // Map<module-name, Map<method-name, method>>
    private val remoteMethods = mutableMapOf<String, Map<String, Method>>()

    // Map<type-name, adapter>
    private val protobufAdapters = mutableMapOf<String, ProtoAdapter<*>>()

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
                    "Module $module not founded"
                )
                return
            }
            invokeImpl(instance, module, method, bytes, callback)
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
                e.message
            )
        }
    }

    private fun invokeImpl(
        instance: RemoteModule,
        module: String,
        method: String,
        param: ByteArray,
        callback: IRemoteCallback?
    ) {
        val methods = remoteMethods.getOrPut(module) {
            val moduleClass = instance::class.java
            moduleClass.declaredMethods
                .asSequence()
                .filter { it.isAnnotationPresent(RemoteMethod::class.java) }
                .filter { it.isAccessible }
                .toList()
                .associateBy { it.getAnnotation(RemoteMethod::class.java)!!.name }
        }
        val remoteMethod = methods[method]
        if (remoteMethod == null) {
            callback?.onError(
                module,
                method,
                OnRemoteCall.ERROR_CODE_METHOD_NOT_FOUNDED,
                "Method $method not founded"
            )
            return
        }
        // handle protobuf param
        val pbArg = remoteMethod.parameters
            .find { it.isAnnotationPresent(RemoteMethodParam::class.java) }
            ?.let { decodeParamFromBytes(it, param) }

        val args = listOfNotNull(
            pbArg,
            callback
        )

        remoteMethod.invoke(instance, args)
    }

    private fun decodeParamFromBytes(
        param: Parameter,
        bytes: ByteArray
    ): Any? {
        val adapter = protobufAdapters.getOrPut(param.type.typeName) {
            val companionObject = Class.forName(param.type.typeName).kotlin.companionObject
            // TODO: fix this
//            Class.forName(param.type.typeName).kotlin.companionObjectInstance
            val adapter = companionObject?.declaredMemberProperties.orEmpty().find { it.name == "ADAPTER" }
            adapter as ProtoAdapter<*>
        }
        return adapter.decode(bytes)
    }

    companion object {
        private const val TAG = "Host-OnRemoteCallImpl"
    }
}

interface RemoteModule {
    val module: String
}

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION)
annotation class RemoteMethod(
    val name: String
)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class RemoteMethodParam
