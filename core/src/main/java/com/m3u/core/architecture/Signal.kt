package com.m3u.core.architecture

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

object Signal {
    private val keys = mutableMapOf<Any, Continuation<Unit>>()
    suspend fun lock(key: Any) {
        if (key in keys)  {
            throw UnsupportedOperationException("Key already locked: $key")
        }
        suspendCancellableCoroutine<Unit> { cont ->
            cont.invokeOnCancellation { keys.remove(key) }
            keys[key] = cont
        }
    }

    fun unlock(key: Any) {
        keys.remove(key)?.resume(Unit)
    }
}