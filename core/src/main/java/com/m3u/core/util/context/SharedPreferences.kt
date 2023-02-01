@file:Suppress("unused")
package com.m3u.core.util.context

import android.content.SharedPreferences
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

fun SharedPreferences.int(
    def: Int = 0,
    key: String? = null,
    onUpdate: ((Int) -> Unit)? = null,
) = delegate(key, def, onUpdate, SharedPreferences::getInt, SharedPreferences.Editor::putInt)

fun SharedPreferences.string(
    def: String? = null,
    key: String? = null,
    onUpdate: ((String?) -> Unit)? = null,
) = delegate(key, def, onUpdate, SharedPreferences::getString, SharedPreferences.Editor::putString)

fun SharedPreferences.long(
    def: Long = 0,
    key: String? = null,
    onUpdate: ((Long) -> Unit)? = null
) = delegate(key, def, onUpdate, SharedPreferences::getLong, SharedPreferences.Editor::putLong)

fun SharedPreferences.boolean(
    def: Boolean = false,
    key: String? = null,
    onUpdate: ((Boolean) -> Unit)? = null,
) = delegate(
    key,
    def,
    onUpdate,
    SharedPreferences::getBoolean,
    SharedPreferences.Editor::putBoolean
)

private inline fun <T> SharedPreferences.delegate(
    key: String? = null,
    defaultValue: T,
    noinline onUpdate: ((T) -> Unit)? = null,
    crossinline getter: SharedPreferences.(String, T) -> T,
    crossinline setter: SharedPreferences.Editor.(String, T) -> SharedPreferences.Editor
): ReadWriteProperty<Any, T> {
    var oldValue: T = defaultValue
    return object : ReadWriteProperty<Any, T> {
        override fun getValue(thisRef: Any, property: KProperty<*>): T {
            return getter(key ?: property.name, defaultValue)
        }

        override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
            if (oldValue != value) {
                oldValue = value
                onUpdate?.invoke(value)
            }
            return edit().setter(key ?: property.name, value).apply()
        }
    }
}