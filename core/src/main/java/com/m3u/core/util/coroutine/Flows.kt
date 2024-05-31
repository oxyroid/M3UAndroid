package com.m3u.core.util.coroutine

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.timeout
import kotlin.time.Duration

@OptIn(FlowPreview::class)
fun <T> Flow<T>.timeout(duration: Duration, block: FlowCollector<T>.() -> Unit) =
    this@timeout.timeout(duration).catch {
        if (it is TimeoutCancellationException) {
            block()
        }
    }

fun <R> flatmapCombined(
    flows: Iterable<Flow<Any>>,
    transform: (keys: Array<Any>) -> Flow<R>
): Flow<R> = combine(flows) { it }.flatMapLatest { keys -> transform(keys) }

@Suppress("UNCHECKED_CAST")
fun <T1, T2, R> flatmapCombined(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    transform: (t1: T1, t2: T2) -> Flow<R>
): Flow<R> where T1 : Any, T2 : Any, R : Any = flatmapCombined(listOf(flow1, flow2)) { keys ->
    transform(keys[0] as T1, keys[1] as T2)
}

@Suppress("UNCHECKED_CAST")
fun <T1, T2, T3, R> flatmapCombined(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    transform: (t1: T1, t2: T2, t3: T3) -> Flow<R>
): Flow<R> where T1 : Any, T2 : Any, T3 : Any, R : Any =
    flatmapCombined(listOf(flow1, flow2, flow3)) { keys ->
        transform(keys[0] as T1, keys[1] as T2, keys[2] as T3)
    }

@Suppress("UNCHECKED_CAST")
fun <T1, T2, T3, T4, R> flatmapCombined(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    transform: (t1: T1, t2: T2, t3: T3, t4: T4) -> Flow<R>
): Flow<R> where T1 : Any, T2 : Any, T3 : Any, T4 : Any, R : Any =
    flatmapCombined(listOf(flow1, flow2, flow3, flow4)) { keys ->
        transform(keys[0] as T1, keys[1] as T2, keys[2] as T3, keys[3] as T4)
    }
