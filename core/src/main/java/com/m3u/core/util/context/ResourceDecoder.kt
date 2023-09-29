package com.m3u.core.util.context

import android.content.Context
import android.content.res.Resources

object ResourceDecoder {
    fun decodeDrawableIds(context: Context, clazz: Class<*>): List<Int> =
        decodeDrawableIds(
            resources = context.resources,
            packageName = context.packageName,
            clazz = clazz
        )

    fun decodeDrawableIds(
        resources: Resources,
        packageName: String,
        clazz: Class<*>
    ): List<Int> = decodeIds(resources, packageName, clazz, "drawable")

    fun decodeStringIds(context: Context, clazz: Class<*>): List<Int> =
        decodeStringIds(
            resources = context.resources,
            packageName = context.packageName,
            clazz = clazz
        )

    fun decodeStringIds(
        resources: Resources,
        packageName: String,
        clazz: Class<*>
    ): List<Int> = decodeIds(resources, packageName, clazz, "string")

    private fun decodeIds(
        resources: Resources,
        packageName: String,
        clazz: Class<*>,
        defType: String
    ): List<Int> = clazz.declaredFields
        .onEach { field ->
            field.isAccessible = true
        }
        .map { field ->
            val name = field.name
            @Suppress("DiscouragedApi")
            resources.getIdentifier(name, defType, packageName)
        }
}