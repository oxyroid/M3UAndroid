package com.m3u.extension.transport.android

import com.m3u.extension.api.InvocationId
import java.io.Closeable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

class ExtensionInvocationRegistryTest {
    @Test
    fun `close cancels locally and rejects a late successful result`() {
        val remoteCancellations = ConcurrentLinkedQueue<InvocationId>()
        val delivered = AtomicReference<Result<String>?>()
        val localCancellations = AtomicInteger()
        val bridge = RecordingCloseable()
        val binderJob = Job()
        val registry = ExtensionInvocationRegistry<String>(remoteCancellations::add)
        val record = ExtensionInvocationRecord(
            invocationId = INVOCATION_ID,
            deliverCompletion = delivered::set,
            deliverCancellation = { localCancellations.incrementAndGet() },
        )
        assertEquals(TransportInvocationRegistration.REGISTERED, registry.register(record))
        record.attachJob(binderJob)
        assertTrue(record.attachBridge(bridge))

        registry.close(CancellationException("transport closed"))
        val lateResultAccepted = registry.complete(record, Result.success("late success"))
        registry.release(record)

        assertFalse(lateResultAccepted)
        assertNull(delivered.get())
        assertEquals(1, localCancellations.get())
        assertEquals(listOf(INVOCATION_ID), remoteCancellations.toList())
        assertTrue(binderJob.isCancelled)
        assertTrue(bridge.closed.get())
    }

    @Test
    fun `concurrent cancel paths share one terminal transition`() {
        repeat(100) { iteration ->
            val remoteCancellations = AtomicInteger()
            val localCancellations = AtomicInteger()
            val delivered = AtomicReference<Result<String>?>()
            val registry = ExtensionInvocationRegistry<String> { remoteCancellations.incrementAndGet() }
            val record = ExtensionInvocationRecord(
                invocationId = InvocationId("call-$iteration"),
                deliverCompletion = delivered::set,
                deliverCancellation = { localCancellations.incrementAndGet() },
            )
            assertEquals(TransportInvocationRegistration.REGISTERED, registry.register(record))
            val start = CountDownLatch(1)
            val outcomes = ConcurrentLinkedQueue<TransportInvocationCancellation>()
            val first = thread(start = true) {
                start.await()
                outcomes += registry.cancel(
                    record,
                    CancellationException("continuation cancelled"),
                )
            }
            val second = thread(start = true) {
                start.await()
                outcomes += registry.cancel(
                    record.invocationId,
                    CancellationException("host cancelled"),
                )
            }

            start.countDown()
            first.join()
            second.join()
            val lateResultAccepted = registry.complete(record, Result.success("late success"))
            registry.release(record)

            assertEquals(1, outcomes.count(TransportInvocationCancellation.CANCELLED::equals))
            assertEquals(1, outcomes.count(TransportInvocationCancellation.ALREADY_TERMINAL::equals))
            assertEquals(1, localCancellations.get())
            assertEquals(1, remoteCancellations.get())
            assertFalse(lateResultAccepted)
            assertNull(delivered.get())
        }
    }

    @Test
    fun `close waits for an accepted completion delivery to finish`() {
        val deliveryStarted = CountDownLatch(1)
        val allowDeliveryToFinish = CountDownLatch(1)
        val closeAttempted = CountDownLatch(1)
        val closeFinished = CountDownLatch(1)
        val completionAccepted = AtomicBoolean(false)
        val registry = ExtensionInvocationRegistry<String> { }
        val record = ExtensionInvocationRecord<String>(
            invocationId = INVOCATION_ID,
            deliverCompletion = {
                deliveryStarted.countDown()
                allowDeliveryToFinish.await()
            },
            deliverCancellation = { error("Accepted completion must not be cancelled") },
        )
        assertEquals(TransportInvocationRegistration.REGISTERED, registry.register(record))
        val completion = thread(start = true) {
            completionAccepted.set(registry.complete(record, Result.success("accepted")))
        }
        assertTrue(deliveryStarted.await(1, TimeUnit.SECONDS))
        val close = thread(start = true) {
            closeAttempted.countDown()
            registry.close(CancellationException("transport closed"))
            closeFinished.countDown()
        }
        assertTrue(closeAttempted.await(1, TimeUnit.SECONDS))

        assertFalse(closeFinished.await(100, TimeUnit.MILLISECONDS))
        allowDeliveryToFinish.countDown()
        completion.join(1_000)
        close.join(1_000)

        assertFalse(completion.isAlive)
        assertFalse(close.isAlive)
        assertTrue(completionAccepted.get())
        assertEquals(0L, closeFinished.count)
        registry.release(record)
    }

    private class RecordingCloseable : Closeable {
        val closed = AtomicBoolean(false)

        override fun close() {
            closed.set(true)
        }
    }

    private companion object {
        val INVOCATION_ID = InvocationId("call-close")
    }
}
