package com.m3u.extension.api

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
        val companionObject = Class.forName(typeName).kotlin.companionObject!!
        val property = companionObject.declaredMembers.first { it.name == "ADAPTER" } as KProperty1<Any, Any>
        property.get(companionObject)
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