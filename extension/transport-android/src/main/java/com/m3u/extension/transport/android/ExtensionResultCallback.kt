package com.m3u.extension.transport.android

import android.os.ParcelFileDescriptor
import com.m3u.extension.transport.android.ipc.IExtensionResultCallback
import java.io.Closeable
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Multiplexes all responses for one extension connection through a single Binder callback.
 *
 * A remote extension can retain the callback proxy, so creating one Binder stub per request would
 * let it grow host Binder state without bound. Request ids keep the single callback reusable while
 * the bounded pending map makes late, duplicate, and unsolicited responses harmless.
 */
class ExtensionResultDispatcher(
    private val maximumPendingResults: Int = DEFAULT_MAXIMUM_PENDING_RESULTS,
) : Closeable {
    private val closed = AtomicBoolean(false)
    private val pending = ConcurrentHashMap<String, PendingResult>()
    private val pendingPermits = Semaphore(maximumPendingResults, true)

    init {
        require(maximumPendingResults > 0) { "Pending result limit must be positive" }
    }

    val callback: IExtensionResultCallback = object : IExtensionResultCallback.Stub() {
        override fun onSuccess(requestId: String?, response: ParcelFileDescriptor?) {
            if (requestId == null || response == null) {
                response?.let { descriptor -> runCatching { descriptor.close() } }
                return
            }
            val result = pending.remove(requestId)
            if (result == null) {
                runCatching { response.close() }
                return
            }
            result.releasePermits()
            result.success(response)
        }

        override fun onFailure(requestId: String?, code: String?, message: String?) {
            if (requestId == null) return
            val result = pending.remove(requestId) ?: return
            result.releasePermits()
            result.failure(
                ExtensionRemoteException(
                    code = code.orEmpty().take(MAX_ERROR_CODE_LENGTH),
                    message = message.orEmpty().take(MAX_ERROR_MESSAGE_LENGTH),
                )
            )
        }
    }

    suspend fun await(
        onCancellation: (requestId: String) -> Unit = {},
        request: (requestId: String, callback: IExtensionResultCallback) -> Unit,
    ): ParcelFileDescriptor = suspendCancellableCoroutine { continuation ->
        if (closed.get()) {
            continuation.resumeFailure(dispatcherClosed())
            return@suspendCancellableCoroutine
        }
        if (!pendingPermits.tryAcquire()) {
            continuation.resumeFailure(IOException("Extension result dispatcher is at capacity"))
            return@suspendCancellableCoroutine
        }
        if (!PROCESS_PENDING_RESULT_PERMITS.tryAcquire()) {
            pendingPermits.release()
            continuation.resumeFailure(IOException("Extension result processing is at capacity"))
            return@suspendCancellableCoroutine
        }

        val requestId = UUID.randomUUID().toString()
        val result = PendingResult(
            continuation = continuation,
            releasePermits = {
                PROCESS_PENDING_RESULT_PERMITS.release()
                pendingPermits.release()
            },
        )
        check(pending.putIfAbsent(requestId, result) == null)

        continuation.invokeOnCancellation {
            if (pending.remove(requestId, result)) {
                result.releasePermits()
                result.cancel()
                runCatching { onCancellation(requestId) }
            }
        }

        if (closed.get()) {
            if (pending.remove(requestId, result)) {
                result.releasePermits()
                result.failure(dispatcherClosed())
            }
            return@suspendCancellableCoroutine
        }
        if (!continuation.isActive || pending[requestId] !== result) {
            return@suspendCancellableCoroutine
        }

        try {
            request(requestId, callback)
        } catch (failure: Exception) {
            if (pending.remove(requestId, result)) {
                result.releasePermits()
                result.failure(failure)
            }
        }
    }

    fun isPending(requestId: String): Boolean = pending.containsKey(requestId)

    override fun close() {
        close(dispatcherClosed())
    }

    fun close(cause: Throwable) {
        if (!closed.compareAndSet(false, true)) return
        pending.entries.toList().forEach { (requestId, result) ->
            if (pending.remove(requestId, result)) {
                result.releasePermits()
                result.failure(cause)
            }
        }
    }

    private fun dispatcherClosed(): IOException =
        IOException("Extension result dispatcher is closed")

    private class PendingResult(
        continuation: CancellableContinuation<ParcelFileDescriptor>,
        private val releasePermits: () -> Unit,
    ) {
        private val continuation = AtomicReference(continuation)
        private val permitsReleased = AtomicBoolean(false)

        fun success(response: ParcelFileDescriptor) {
            val target = continuation.getAndSet(null)
            if (target == null) {
                runCatching { response.close() }
                return
            }
            val ownedResponse = runCatching {
                response.use { current ->
                    ParcelFileDescriptor.dup(current.fileDescriptor)
                }
            }.getOrElse { failure ->
                target.resumeFailure(failure)
                return
            }
            if (!target.isActive) {
                runCatching { ownedResponse.close() }
            } else {
                runCatching {
                    target.resume(ownedResponse) { _, descriptor, _ ->
                        runCatching { descriptor.close() }
                    }
                }.onFailure {
                    runCatching { ownedResponse.close() }
                }
            }
        }

        fun failure(failure: Throwable) {
            continuation.getAndSet(null)?.resumeFailure(failure)
        }

        fun cancel() {
            continuation.set(null)
        }

        fun releasePermits() {
            if (permitsReleased.compareAndSet(false, true)) releasePermits.invoke()
        }
    }

    private companion object {
        const val DEFAULT_MAXIMUM_PENDING_RESULTS = 16
        const val MAX_PROCESS_PENDING_RESULTS = 64
        val PROCESS_PENDING_RESULT_PERMITS = Semaphore(MAX_PROCESS_PENDING_RESULTS, true)
    }
}

class ExtensionRemoteException(
    val code: String,
    message: String,
) : IOException("Extension IPC failed ($code): $message")

internal fun <T> CancellableContinuation<T>.resumeFailure(failure: Throwable) {
    if (isActive) runCatching { resumeWithException(failure) }
}

private const val MAX_ERROR_CODE_LENGTH = 128
private const val MAX_ERROR_MESSAGE_LENGTH = 512
