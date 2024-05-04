package com.m3u.data.api

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import okhttp3.ResponseBody

object WebPageManager {
    private val source: MutableSharedFlow<ResponseBody> = MutableSharedFlow()
    private val coroutineScope = CoroutineScope(SupervisorJob())
    fun push(body: ResponseBody) {
        source.tryEmit(body)
    }

    private val jobs = mutableListOf<Job>()

    fun observe(block: (ResponseBody) -> Unit) {
        jobs += source
            .onEach { block(it) }
            .launchIn(coroutineScope)
    }

    fun observe(lifecycle: Lifecycle, block: (ResponseBody) -> Unit) {
        jobs += source
            .onEach { block(it) }
            .flowWithLifecycle(lifecycle)
            .launchIn(coroutineScope)
    }

    fun removeAllObservers() {
        val iterator = jobs.listIterator()
        while (iterator.hasNext()) {
            val job = iterator.next()
            if (!job.isCompleted) {
                job.cancel()
            }
            iterator.remove()
        }
    }
}