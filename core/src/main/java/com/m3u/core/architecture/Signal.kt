package com.m3u.core.architecture

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

object Signal {
    private val keys = mutableMapOf<Any, Continuation<Unit>>()
    suspend fun lock(key: Any): Unit = suspendCancellableCoroutine { continuation ->
        if (key in keys) {
            throw UnsupportedOperationException("Key already locked: $key")
        }
        keys[key] = continuation
        continuation.invokeOnCancellation {
            keys.remove(key)
        }
    }

    fun unlock(key: Any) {
        keys.remove(key)?.resume(Unit)
    }
}