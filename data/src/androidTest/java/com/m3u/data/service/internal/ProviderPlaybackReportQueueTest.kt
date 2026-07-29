package com.m3u.data.service.internal

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderPlaybackReportQueueTest {
    @Test
    fun reportsPreserveEmissionOrderIncludingBackwardSeek() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val queue = ProviderPlaybackReportQueue(scope)
            val positions = mutableListOf<Long>()
            val firstEntered = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()

            queue.enqueue(SESSION_ID) {
                firstEntered.complete(Unit)
                releaseFirst.await()
                positions += 610_000_000L
            }
            firstEntered.await()
            queue.enqueue(SESSION_ID) {
                positions += 200_000_000L
            }

            val drained = async(Dispatchers.Default) {
                queue.sealAndAwaitDrained(SESSION_ID)
            }
            releaseFirst.complete(Unit)
            withTimeout(TEST_TIMEOUT_MILLIS) {
                drained.await()
            }

            assertEquals(listOf(610_000_000L, 200_000_000L), positions)
            assertNull(queue.enqueue(SESSION_ID) { positions += 300_000_000L })
        } finally {
            scope.cancel()
        }
    }

    private companion object {
        const val SESSION_ID = "session"
        const val TEST_TIMEOUT_MILLIS = 5_000L
    }
}
