package com.m3u.extension.transport.android

import com.m3u.extension.api.InvocationId
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

internal enum class TransportInvocationRegistration {
    REGISTERED,
    DUPLICATE,
    CLOSED,
}

internal enum class TransportInvocationCancellation {
    CANCELLED,
    ALREADY_TERMINAL,
    NOT_FOUND,
}

/**
 * Serializes invocation completion against local cancellation and transport close.
 * Records stay registered until their Binder job exits so concurrent cancel paths share one terminal state.
 */
internal class ExtensionInvocationRegistry<T>(
    private val requestRemoteCancel: (InvocationId) -> Unit,
) {
    private val lock = Any()
    private val records = mutableMapOf<InvocationId, ExtensionInvocationRecord<T>>()
    private var closed = false

    fun register(record: ExtensionInvocationRecord<T>): TransportInvocationRegistration =
        synchronized(lock) {
            when {
                closed -> TransportInvocationRegistration.CLOSED
                record.invocationId in records -> TransportInvocationRegistration.DUPLICATE
                else -> {
                    records[record.invocationId] = record
                    TransportInvocationRegistration.REGISTERED
                }
            }
        }

    fun complete(record: ExtensionInvocationRecord<T>, result: Result<T>): Boolean {
        return synchronized(lock) {
            val shouldDeliver =
                !closed && records[record.invocationId] === record && record.markCompleted()
            if (shouldDeliver) record.finishCompletion(result)
            shouldDeliver
        }
    }

    fun cancel(
        record: ExtensionInvocationRecord<T>,
        cause: CancellationException,
    ): TransportInvocationCancellation = cancel(record.invocationId, record, cause)

    fun cancel(
        invocationId: InvocationId,
        cause: CancellationException,
    ): TransportInvocationCancellation = cancel(invocationId, expected = null, cause)

    fun release(record: ExtensionInvocationRecord<T>) {
        synchronized(lock) {
            if (records[record.invocationId] === record) records.remove(record.invocationId)
        }
        record.closeBridge()
    }

    fun close(cause: CancellationException) {
        val cancelled = synchronized(lock) {
            if (closed) return
            closed = true
            val cancelledRecords = records.values
                .filter { record -> record.markCancelled(cause) }
            records.clear()
            cancelledRecords.forEach { record -> record.finishCancellation(cause) }
            cancelledRecords
        }
        cancelled.forEach { record ->
            requestRemoteCancel(record.invocationId)
        }
    }

    fun fail(cause: Throwable) {
        require(cause !is CancellationException) {
            "Transport failures must not use coroutine cancellation"
        }
        val failed = synchronized(lock) {
            if (closed) return
            closed = true
            val failedRecords = records.values
                .filter { record -> record.markCompleted() }
            records.clear()
            failedRecords.forEach { record -> record.finishTransportFailure(cause) }
            failedRecords
        }
        failed.forEach { record ->
            requestRemoteCancel(record.invocationId)
        }
    }

    private fun cancel(
        invocationId: InvocationId,
        expected: ExtensionInvocationRecord<T>?,
        cause: CancellationException,
    ): TransportInvocationCancellation {
        val outcome = synchronized(lock) {
            val record = records[invocationId]
                ?: return@synchronized TransportInvocationCancellation.NOT_FOUND
            if (expected != null && record !== expected) {
                return@synchronized TransportInvocationCancellation.NOT_FOUND
            }
            if (record.markCancelled(cause)) {
                record.finishCancellation(cause)
                TransportInvocationCancellation.CANCELLED
            } else {
                TransportInvocationCancellation.ALREADY_TERMINAL
            }
        }
        if (outcome == TransportInvocationCancellation.CANCELLED) {
            requestRemoteCancel(invocationId)
        }
        return outcome
    }
}

internal class ExtensionInvocationRecord<T>(
    val invocationId: InvocationId,
    private val deliverCompletion: (Result<T>) -> Unit,
    private val deliverCancellation: (CancellationException) -> Unit,
) {
    private val state = AtomicReference(State.ACTIVE)
    private val cancellationCause = AtomicReference<CancellationException?>()
    private val binderJob = AtomicReference<Job?>()
    private val bridgeLease = InvocationBridgeLease()

    val isActive: Boolean
        get() = state.get() == State.ACTIVE

    fun attachJob(job: Job) {
        check(binderJob.compareAndSet(null, job)) { "Invocation Binder job is already attached" }
        if (state.get() != State.ACTIVE) {
            job.cancel(
                cancellationCause.get()
                    ?: CancellationException("Extension invocation is no longer active")
            )
        }
    }

    fun attachBridge(bridge: Closeable): Boolean {
        if (!isActive) {
            runCatching { bridge.close() }
            return false
        }
        if (!bridgeLease.attach(bridge)) return false
        if (!isActive) {
            bridgeLease.close()
            return false
        }
        return true
    }

    internal fun markCompleted(): Boolean = state.compareAndSet(State.ACTIVE, State.COMPLETED)

    internal fun markCancelled(cause: CancellationException): Boolean {
        if (!state.compareAndSet(State.ACTIVE, State.CANCELLED)) return false
        cancellationCause.set(cause)
        return true
    }

    internal fun finishCompletion(result: Result<T>) {
        bridgeLease.close()
        deliverCompletion(result)
    }

    internal fun finishCancellation(cause: CancellationException) {
        bridgeLease.close()
        binderJob.get()?.cancel(cause)
        deliverCancellation(cause)
    }

    internal fun finishTransportFailure(cause: Throwable) {
        bridgeLease.close()
        val internalCancellation =
            CancellationException("Extension transport became unavailable").also { cancellation ->
                cancellation.initCause(cause)
            }
        cancellationCause.compareAndSet(null, internalCancellation)
        binderJob.get()?.cancel(internalCancellation)
        deliverCompletion(Result.failure(cause))
    }

    fun closeBridge() {
        bridgeLease.close()
    }

    private enum class State {
        ACTIVE,
        CANCELLED,
        COMPLETED,
    }
}

internal class InvocationBridgeLease : Closeable {
    private val closed = AtomicBoolean(false)
    private val resource = AtomicReference<Closeable?>()

    fun attach(candidate: Closeable): Boolean {
        check(resource.compareAndSet(null, candidate)) {
            "Invocation bridge is already attached"
        }
        if (closed.get()) {
            resource.getAndSet(null)?.let { current -> runCatching { current.close() } }
            return false
        }
        return true
    }

    override fun close() {
        closed.set(true)
        resource.getAndSet(null)?.let { current -> runCatching { current.close() } }
    }
}
