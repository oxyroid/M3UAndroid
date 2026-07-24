package com.m3u.business.setting

import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExtensionSettingsOperationQueueTest {
    @Test
    fun `updates run in submission order`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val queue = ExtensionSettingsOperationQueue(scope) { failure -> throw failure }
        val events = Collections.synchronizedList(mutableListOf<String>())
        val firstStarted = CompletableDeferred<Unit>()
        val finishFirst = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()

        try {
            val first = queue.launchUpdate(EXTENSION_ID) {
                events += "first-start"
                firstStarted.complete(Unit)
                finishFirst.await()
                events += "first-end"
            }
            firstStarted.await()
            val second = queue.launchUpdate(EXTENSION_ID) {
                events += "second-start"
                secondStarted.complete(Unit)
                events += "second-end"
            }

            assertFalse(secondStarted.isCompleted)
            finishFirst.complete(Unit)
            joinAll(first, second)

            assertEquals(
                listOf("first-start", "first-end", "second-start", "second-end"),
                events,
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `destructive operation waits for cancelled update before clearing`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val queue = ExtensionSettingsOperationQueue(scope) { failure -> throw failure }
        val persistedValues = Collections.synchronizedList(mutableListOf("initial"))
        val events = Collections.synchronizedList(mutableListOf<String>())
        val nonCancellableUpdateStarted = CompletableDeferred<Unit>()
        val finishOldUpdate = CompletableDeferred<Unit>()
        val destructiveStarted = CompletableDeferred<Unit>()

        try {
            val oldUpdate = queue.launchUpdate(EXTENSION_ID) {
                withContext(NonCancellable) {
                    nonCancellableUpdateStarted.complete(Unit)
                    finishOldUpdate.await()
                    persistedValues += "stale"
                    events += "stale-write"
                }
            }
            nonCancellableUpdateStarted.await()
            val queuedUpdate = queue.launchUpdate(EXTENSION_ID) {
                persistedValues += "cancelled"
                events += "cancelled-write"
            }
            val destructive = queue.launchDestructive(EXTENSION_ID) {
                destructiveStarted.complete(Unit)
                persistedValues.clear()
                events += "clear"
            }
            val laterUpdate = queue.launchUpdate(EXTENSION_ID) {
                persistedValues += "fresh"
                events += "fresh-write"
            }

            assertTrue(oldUpdate.isCancelled)
            assertFalse(destructiveStarted.isCompleted)
            finishOldUpdate.complete(Unit)
            joinAll(oldUpdate, queuedUpdate, destructive, laterUpdate)

            assertEquals(listOf("stale-write", "clear", "fresh-write"), events)
            assertEquals(listOf("fresh"), persistedValues)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `failure is reported and does not block the next operation`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val reportedFailure = CompletableDeferred<Exception>()
        val queue = ExtensionSettingsOperationQueue(scope) { failure ->
            reportedFailure.complete(failure)
        }
        val nextOperationStarted = CompletableDeferred<Unit>()

        try {
            val failed = queue.launchUpdate(EXTENSION_ID) {
                error("expected failure")
            }
            val next = queue.launchOperation(EXTENSION_ID) {
                nextOperationStarted.complete(Unit)
            }
            joinAll(failed, next)

            assertEquals("expected failure", reportedFailure.await().message)
            assertTrue(nextOperationStarted.isCompleted)
            assertFalse(failed.isCancelled)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `ordinary operation waits for a destructive barrier`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val queue = ExtensionSettingsOperationQueue(scope) { failure -> throw failure }
        val finishDestructive = CompletableDeferred<Unit>()
        val operationStarted = CompletableDeferred<Unit>()

        try {
            val destructive = queue.launchDestructive(EXTENSION_ID) {
                finishDestructive.await()
            }
            val operation = queue.launchOperation(EXTENSION_ID) {
                operationStarted.complete(Unit)
            }

            assertFalse(operationStarted.isCompleted)
            finishDestructive.complete(Unit)
            joinAll(destructive, operation)
            assertTrue(operationStarted.isCompleted)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `cancelling a queued operation does not bypass an older barrier`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val queue = ExtensionSettingsOperationQueue(scope) { failure -> throw failure }
        val destructiveStarted = CompletableDeferred<Unit>()
        val finishDestructive = CompletableDeferred<Unit>()
        val laterOperationStarted = CompletableDeferred<Unit>()

        try {
            val destructive = queue.launchDestructive(EXTENSION_ID) {
                destructiveStarted.complete(Unit)
                finishDestructive.await()
            }
            destructiveStarted.await()
            val cancelledOperation = queue.launchOperation(EXTENSION_ID) {
                error("cancelled operation must not run")
            }
            cancelledOperation.cancel()
            cancelledOperation.join()
            val laterOperation = queue.launchOperation(EXTENSION_ID) {
                laterOperationStarted.complete(Unit)
            }

            assertFalse(laterOperationStarted.isCompleted)
            finishDestructive.complete(Unit)
            joinAll(destructive, laterOperation)
            assertTrue(laterOperationStarted.isCompleted)
        } finally {
            scope.cancel()
        }
    }

    private companion object {
        const val EXTENSION_ID = "com.m3u.test.settings"
    }
}
