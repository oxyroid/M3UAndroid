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
    vararg flows: Flow<*>,
    transform: (values: List<Any?>) -> Flow<R>,
): Flow<R> =
    combine(flows.asList()) { it.toList() }
        .flatMapLatest { values -> transform(values) }

fun <T1, T2, R> flatmapCombined(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    transform: (t1: T1, t2: T2) -> Flow<R>,
): Flow<R> =
    combine(
        flow1,
        flow2
    ) { t1, t2 -> t1 to t2 }
        .flatMapLatest { (t1, t2) ->
            transform(
                t1,
                t2
            )
        }

fun <T1, T2, T3, R> flatmapCombined(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    transform: (t1: T1, t2: T2, t3: T3) -> Flow<R>,
): Flow<R> =
    combine(
        flow1,
        flow2,
        flow3
    ) { t1, t2, t3 ->
        Triple(
            t1,
            t2,
            t3
        )
    }
        .flatMapLatest { (t1, t2, t3) ->
            transform(
                t1,
                t2,
                t3
            )
        }

fun <T1, T2, T3, T4, R> flatmapCombined(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    transform: (t1: T1, t2: T2, t3: T3, t4: T4) -> Flow<R>,
): Flow<R> =
    combine(
        flow1,
        flow2,
        flow3,
        flow4
    ) { t1, t2, t3, t4 ->
        transform(
            t1,
            t2,
            t3,
            t4
        )
    }.flatMapLatest { it }
