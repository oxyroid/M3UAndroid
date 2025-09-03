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
        val shouldFlush = mutex.withLock {
            cache += element
            cache.size >= limit
        }
        
        if (shouldFlush) {
            val snapshot = mutex.withLock {
                if (cache.size >= limit) {
                    val snapshot = cache.toList()
                    cache.clear()
                    snapshot
                } else null
            }
            snapshot?.let { onReceived(it) }
        }
    }

    suspend fun flush() {
        val snapshot = mutex.withLock {
            if (cache.isNotEmpty()) {
                val snapshot = cache.toList()
                cache.clear()
                snapshot
            } else null
        }
        snapshot?.let { onReceived(it) }
    }
}