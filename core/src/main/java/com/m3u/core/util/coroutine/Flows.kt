package com.m3u.core.util.coroutine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.reflect.KProperty


operator fun <T> MutableStateFlow<T>.setValue(ref: Any, property: KProperty<*>, t: T) {
    value = t
}

operator fun <T> StateFlow<T>.getValue(ref: Any, property: KProperty<*>): T {
    return value
}
