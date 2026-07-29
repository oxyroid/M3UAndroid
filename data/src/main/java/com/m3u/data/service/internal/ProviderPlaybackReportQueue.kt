package com.m3u.data.service.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Serializes reports for one remote playback session in the order the player emitted them.
 *
 * Sealing a session rejects reports racing with shutdown and returns only after every report
 * accepted before the seal has completed.
 */
internal class ProviderPlaybackReportQueue(
    private val scope: CoroutineScope,
) {
    private val lock = Any()
    private val pendingBySession = mutableMapOf<String, Job>()
    private val sealedSessionIds = LinkedHashSet<String>()

    fun enqueue(
        sessionId: String,
        report: suspend () -> Unit,
    ): Job? {
        require(sessionId.isNotBlank())
        lateinit var job: Job
        synchronized(lock) {
            if (sessionId in sealedSessionIds) return null
            val previous = pendingBySession[sessionId]
            job = scope.launch(start = CoroutineStart.LAZY) {
                previous?.join()
                report()
            }
            pendingBySession[sessionId] = job
        }
        job.invokeOnCompletion {
            synchronized(lock) {
                pendingBySession.remove(sessionId, job)
            }
        }
        job.start()
        return job
    }

    suspend fun sealAndAwaitDrained(sessionId: String) {
        require(sessionId.isNotBlank())
        val pending = synchronized(lock) {
            sealedSessionIds += sessionId
            while (sealedSessionIds.size > MAX_RECENTLY_SEALED_SESSIONS) {
                sealedSessionIds.remove(sealedSessionIds.first())
            }
            pendingBySession[sessionId]
        }
        pending?.join()
    }

    private companion object {
        const val MAX_RECENTLY_SEALED_SESSIONS = 256
    }
}
