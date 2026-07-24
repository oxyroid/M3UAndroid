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
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderSessionCloseQueueTest {
    @Test
    fun awaitDrainedIncludesWorkEnqueuedWhileAnEarlierCloseIsRunning() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val queue = ProviderSessionCloseQueue(scope)
            val order = mutableListOf<String>()
            val firstEntered = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            val secondEntered = CompletableDeferred<Unit>()
            val releaseSecond = CompletableDeferred<Unit>()

            queue.enqueue(ACCOUNT_A) {
                firstEntered.complete(Unit)
                releaseFirst.await()
                order += "first"
            }
            firstEntered.await()
            val drained = async(Dispatchers.Default) {
                queue.awaitDrained(ACCOUNT_A)
            }
            queue.enqueue(ACCOUNT_A) {
                secondEntered.complete(Unit)
                releaseSecond.await()
                order += "second"
            }
            withTimeout(TEST_TIMEOUT_MILLIS) {
                queue.awaitDrained(ACCOUNT_B)
            }

            releaseFirst.complete(Unit)
            withTimeout(TEST_TIMEOUT_MILLIS) {
                secondEntered.await()
            }
            assertFalse(drained.completesWithinShortWait())

            releaseSecond.complete(Unit)
            withTimeout(TEST_TIMEOUT_MILLIS) {
                drained.await()
            }
            assertEquals(listOf("first", "second"), order)
        } finally {
            scope.cancel()
        }
    }

    private suspend fun kotlinx.coroutines.Deferred<Unit>.completesWithinShortWait(): Boolean =
        withTimeoutOrNull(SHORT_WAIT_MILLIS) {
            await()
            true
        } == true

    private companion object {
        const val SHORT_WAIT_MILLIS = 100L
        const val TEST_TIMEOUT_MILLIS = 5_000L
        const val ACCOUNT_A = "account-a"
        const val ACCOUNT_B = "account-b"
    }
}
