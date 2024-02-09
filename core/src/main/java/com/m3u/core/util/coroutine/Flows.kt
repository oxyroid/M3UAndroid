package com.m3u.core.util.coroutine

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.timeout
import kotlin.reflect.KProperty
import kotlin.time.Duration


operator fun <T> MutableStateFlow<T>.setValue(ref: Any, property: KProperty<*>, t: T) {
    value = t
}

operator fun <T> StateFlow<T>.getValue(ref: Any, property: KProperty<*>): T {
    return value
}

@OptIn(FlowPreview::class)
fun <T> Flow<T>.onTimeout(duration: Duration, block: FlowCollector<T>.() -> Unit) =
    timeout(duration).catch {
        if (it is TimeoutCancellationException) {
            block()
        }
    }
