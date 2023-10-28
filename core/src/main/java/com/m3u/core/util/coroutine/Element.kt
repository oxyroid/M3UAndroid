@file:Suppress("unused")

package com.m3u.core.util.coroutine

import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@JvmName("onIterableEachElement")
inline fun <T> Flow<Iterable<T>>.onEachElement(
    crossinline block: suspend (T) -> Unit
): Flow<Iterable<T>> {
    var job: Job? = null
    return onEach { collection ->
        job?.cancel()
        job = coroutineScope {
            launch {
                collection.forEach {
                    block(it)
                }
            }
        }
    }
}

@JvmName("mapIterableElement")
inline fun <T, R> Flow<Iterable<T>>.mapElement(
    crossinline block: (T) -> R
): Flow<Iterable<R>> {
    return map { element ->
        element.map {
            block(it)
        }
    }
}

@JvmName("onCollectionEachElement")
inline fun <T> Flow<Collection<T>>.onEachElement(
    crossinline block: suspend (T) -> Unit
): Flow<Collection<T>> {
    var job: Job? = null
    return onEach { collection ->
        job?.cancel()
        job = coroutineScope {
            launch {
                collection.forEach {
                    block(it)
                }
            }
        }
    }
}

@JvmName("mapCollectionElement")
inline fun <T, R> Flow<Collection<T>>.mapElement(
    crossinline block: (T) -> R
): Flow<Collection<R>> {
    return map { element ->
        element.map {
            block(it)
        }
    }
}

@JvmName("onListEachElement")
inline fun <T> Flow<List<T>>.onEachElement(
    crossinline block: (T) -> Unit
): Flow<List<T>> {
    return onEach { collection ->
        collection.forEach {
            block(it)
        }
    }
}

@JvmName("mapListElement")
inline fun <T, R> Flow<List<T>>.mapElement(
    crossinline block: (T) -> R
): Flow<List<R>> {
    return map { element ->
        element.map {
            block(it)
        }
    }
}
