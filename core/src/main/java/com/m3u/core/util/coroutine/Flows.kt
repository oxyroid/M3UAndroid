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
    timeout(duration).catch { throwable ->
        when (throwable) {
            is TimeoutCancellationException -> block()
            else -> throw throwable // Re-throw other exceptions
        }
    }

fun <R> flatmapCombined(
    flows: Iterable<Flow<Any?>>,
    transform: (keys: Array<Any?>) -> Flow<R>
): Flow<R> = combine(flows) { it }.flatMapLatest(transform)

inline fun <T1, T2, R> flatmapCombined(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    crossinline transform: (t1: T1, t2: T2) -> Flow<R>
): Flow<R> = combine(flow1, flow2) { t1, t2 -> t1 to t2 }
    .flatMapLatest { (t1, t2) -> transform(t1, t2) }

inline fun <T1, T2, T3, R> flatmapCombined(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    crossinline transform: (t1: T1, t2: T2, t3: T3) -> Flow<R>
): Flow<R> = combine(flow1, flow2, flow3) { t1, t2, t3 -> Triple(t1, t2, t3) }
    .flatMapLatest { (t1, t2, t3) -> transform(t1, t2, t3) }

inline fun <T1, T2, T3, T4, R> flatmapCombined(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    crossinline transform: (t1: T1, t2: T2, t3: T3, t4: T4) -> Flow<R>
): Flow<R> = combine(flow1, flow2, flow3, flow4) { flows -> flows }
    .flatMapLatest { flows -> transform(flows[0] as T1, flows[1] as T2, flows[2] as T3, flows[3] as T4) }
