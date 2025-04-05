package com.m3u.extension.api

import com.squareup.wire.ProtoAdapter
import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import kotlin.reflect.KProperty1
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.declaredMembers

object Utils {
    @Suppress("UNCHECKED_CAST")
    fun getAdapter(typeName: String): Any = Samplings.measure("adapter") {
        val companionObject = checkNotNull(Class.forName(typeName).kotlin.companionObject) {
            "Companion object not found for $typeName."
        }
        val property =
            companionObject.declaredMembers.first { it.name == "ADAPTER" } as KProperty1<Any, Any>
        property.get(companionObject)
    }

    fun <T : Any> encode(adapter: Any, obj: T): ByteArray {
        return ProtoAdapter::class.java
            .declaredMethods
            .first { it.name == "encode" && it.returnType == ByteArray::class.java }
            .invoke(adapter, obj) as ByteArray
    }

    @Suppress("UNCHECKED_CAST")
    fun decode(adapter: Any, bytes: ByteArray): Any {
        val raw = adapter as? ProtoAdapter<Any>
        return raw?.decode(bytes) ?: (ProtoAdapter::class.java
            .getDeclaredMethod("decode", ByteArray::class.java)
            .invoke(adapter, bytes))
            .let { checkNotNull(it) { "Failed to decode, adapter: $adapter, bytes size: ${bytes.size}." } }
    }

    fun Parameter.getRealParameterizedType(): Type {
        val type = (parameterizedType as ParameterizedType).actualTypeArguments[0]
        return when (type) {
            is WildcardType -> {
                type.lowerBounds.firstOrNull() ?: type.upperBounds.firstOrNull() ?: type
            }

            else -> type
        }
    }
}