package com.m3u.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun <E> createCoroutineCache(
    limit: Int,
    onReceived: suspend (cache: List<E>) -> Unit
): CoroutineCache<E> = object : CoroutineCache<E>(limit) {
    override suspend fun onReceived(cache: List<E>) {
        onReceived(cache)
    }
}

internal abstract class CoroutineCache<E>(
    private val limit: Int
) {
    private val cache = mutableListOf<E>()
    private val mutex = Mutex()
    abstract suspend fun onReceived(cache: List<E>)
    suspend fun push(element: E) {
        cache += element
        if (cache.size >= limit) {
            mutex.withLock {
                // check again
                if (cache.size >= limit) {
                    onReceived(cache)
                    cache.clear()
                }
            }
        }
    }

    suspend fun flush() {
        mutex.withLock {
            onReceived(cache)
            cache.clear()
        }
    }
}