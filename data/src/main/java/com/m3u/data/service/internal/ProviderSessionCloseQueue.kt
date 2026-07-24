package com.m3u.data.service.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Preserves server-side session ordering while keeping non-suspending player callbacks cheap.
 */
internal class ProviderSessionCloseQueue(
    private val scope: CoroutineScope,
) {
    private val lock = Any()
    private val pendingByAccount = mutableMapOf<String, Job>()

    fun enqueue(
        accountId: String,
        close: suspend () -> Unit,
    ): Job {
        require(accountId.isNotBlank())
        lateinit var job: Job
        synchronized(lock) {
            val previous = pendingByAccount[accountId]
            job = scope.launch(start = CoroutineStart.LAZY) {
                previous?.join()
                close()
            }
            pendingByAccount[accountId] = job
        }
        job.invokeOnCompletion {
            synchronized(lock) {
                pendingByAccount.remove(accountId, job)
            }
        }
        job.start()
        return job
    }

    suspend fun awaitDrained(accountId: String) {
        require(accountId.isNotBlank())
        while (true) {
            val current = synchronized(lock) {
                pendingByAccount[accountId]
            } ?: return
            current.join()
            val drained = synchronized(lock) {
                val pending = pendingByAccount[accountId]
                pending == null || pending === current
            }
            if (drained) return
        }
    }
}
