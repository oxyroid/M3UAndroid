package com.m3u.business.channel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal class CwPositionConsumer(
    private val flow: Flow<Long>,
    coroutineScope: CoroutineScope,
    private val onConsume: suspend CoroutineScope.(cwPosition: Long) -> Unit
) {
    private var deferred: CompletableDeferred<Unit>? = null

    // @IgnorableReturnValue
    fun notifyConsumed(): Boolean {
        deferred?.complete(Unit)
        val consumed = deferred != null
        deferred = null
        return consumed
    }

    init {
        coroutineScope.launch { launchImpl() }
    }

    private suspend fun launchImpl() = coroutineScope {
        flow.collectLatest { newValue ->
            ensureActive()
            onConsume(newValue)
            if (newValue != -1L) {
                deferred = CompletableDeferred<Unit>().apply {
                    await()
                }
                ensureActive()
                onConsume(-1L)
            }
        }
    }
}