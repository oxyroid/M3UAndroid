package com.m3u.extension.transport.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import com.m3u.extension.api.ExtensionApiVersion
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.security.BrokerProtocolVersions
import com.m3u.extension.api.InvocationId
import com.m3u.extension.api.SerializedExtensionEnvelope
import com.m3u.extension.api.SerializedExtensionResult
import com.m3u.extension.runtime.ExtensionTransport
import com.m3u.extension.runtime.ExtensionTransportHealth
import com.m3u.extension.transport.android.ipc.IExtensionHostBridge
import com.m3u.extension.transport.android.ipc.IExtensionResultCallback
import com.m3u.extension.transport.android.ipc.IExtensionService
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AndroidBoundExtensionTransport private constructor(
    private val context: Context,
    private val connection: ServiceConnection,
    private val service: IExtensionService,
    private val serviceUid: Int,
    override val manifest: ExtensionManifest,
    private val hostBridgeFactory: (
        ExtensionManifest,
        SerializedExtensionEnvelope,
    ) -> IExtensionHostBridge,
    private val json: Json,
    private val connectionState: ExtensionConnectionState,
    private val resultDispatcher: ExtensionResultDispatcher,
) : ExtensionTransport, Closeable {
    private val closed = AtomicBoolean(false)
    private val binderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val remoteCancelScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val binderInvocationPermits = Semaphore(MAX_OUTSTANDING_BINDER_INVOCATIONS, true)
    private val brokerRequestPermits = Semaphore(MAX_OUTSTANDING_BROKER_REQUESTS, true)
    private val remoteCancelPermits = Semaphore(MAX_OUTSTANDING_REMOTE_CANCELS, true)
    private val invocations = ExtensionInvocationRegistry<SerializedExtensionResult>(::requestRemoteCancel)
    private val deathRecipient = IBinder.DeathRecipient(connectionState::markUnavailable)
    private val binderCallLock = Any()

    val isConnectionAvailable: Boolean
        get() = connectionState.isAvailable && !closed.get() && service.asBinder().isBinderAlive

    init {
        service.asBinder().linkToDeath(deathRecipient, 0)
        connectionState.setUnavailableListener(::terminateInFlightOperations)
    }

    override suspend fun invoke(request: SerializedExtensionEnvelope): SerializedExtensionResult =
        suspendCancellableCoroutine { continuation ->
            val record = ExtensionInvocationRecord(
                invocationId = request.invocationId,
                deliverCompletion = { result -> continuation.resumeResult(result) },
                deliverCancellation = continuation::cancel,
            )
            when (invocations.register(record)) {
                TransportInvocationRegistration.REGISTERED -> Unit
                TransportInvocationRegistration.DUPLICATE -> {
                    continuation.resumeResult(
                        Result.failure(IllegalStateException("Invocation id is already active"))
                    )
                    return@suspendCancellableCoroutine
                }
                TransportInvocationRegistration.CLOSED -> {
                    continuation.resumeResult(
                        Result.failure(IllegalStateException("Extension transport is closed"))
                    )
                    return@suspendCancellableCoroutine
                }
            }
            continuation.invokeOnCancellation { cause ->
                invocations.cancel(record, cause.asInvocationCancellation())
            }
            val binderJob = binderScope.launch {
                var binderPermitAcquired = false
                var processBinderPermitAcquired = false
                val result = runCatching {
                    try {
                        check(record.isActive) { "Extension invocation was cancelled" }
                        check(isConnectionAvailable) { "Extension service is unavailable" }
                        binderPermitAcquired = binderInvocationPermits.tryAcquire()
                        check(binderPermitAcquired) {
                            "Extension service has too many outstanding Binder invocations"
                        }
                        processBinderPermitAcquired =
                            PROCESS_BINDER_INVOCATION_PERMITS.tryAcquire()
                        check(processBinderPermitAcquired) {
                            "Host has too many outstanding extension invocations"
                        }
                        val bridge = RevocableExtensionHostBridge(
                            delegate = hostBridgeFactory(manifest, request),
                            expectedUid = serviceUid,
                            requestPermits = brokerRequestPermits,
                        )
                        check(record.attachBridge(bridge)) {
                            "Extension invocation was cancelled"
                        }
                        check(record.isActive) { "Extension invocation was cancelled" }
                        val responseFile = ParcelFileCodec.write(
                            context,
                            json.encodeToString(request),
                        ).use { requestFile ->
                            resultDispatcher.await { resultRequestId, callback ->
                                synchronized(binderCallLock) {
                                    check(record.isActive) {
                                        "Extension invocation was cancelled"
                                    }
                                    service.invoke(
                                        resultRequestId,
                                        request.invocationId.value,
                                        request.hook.id,
                                        request.schemaVersion,
                                        requestFile,
                                        bridge,
                                        callback,
                                    )
                                }
                            }
                        }
                        responseFile.use { response ->
                            check(record.isActive) { "Extension invocation was cancelled" }
                            check(isConnectionAvailable) { "Extension service is unavailable" }
                            val responsePayload =
                                ParcelFileCodec.readInterruptibly(response, MAX_RESPONSE_BYTES)
                            responsePayload.requireSafeExtensionJsonDepth()
                            json.decodeFromString<SerializedExtensionResult>(responsePayload)
                        }
                    } finally {
                        if (processBinderPermitAcquired) {
                            PROCESS_BINDER_INVOCATION_PERMITS.release()
                        }
                        if (binderPermitAcquired) binderInvocationPermits.release()
                    }
                }
                invocations.complete(record, result)
            }
            record.attachJob(binderJob)
            binderJob.invokeOnCompletion { failure ->
                if (failure != null) invocations.complete(record, Result.failure(failure))
                invocations.release(record)
            }
        }

    override suspend fun cancel(invocationId: InvocationId) {
        val outcome = invocations.cancel(
            invocationId,
            CancellationException("Extension invocation was cancelled by the host"),
        )
        if (outcome == TransportInvocationCancellation.NOT_FOUND) {
            requestRemoteCancel(invocationId)
        }
    }

    override suspend fun health(): ExtensionTransportHealth = withContext(Dispatchers.IO) {
        if (!isConnectionAvailable) return@withContext ExtensionTransportHealth.UNAVAILABLE
        try {
            val health = awaitControlResult(
                dispatcher = resultDispatcher,
                maximumBytes = MAX_HEALTH_BYTES,
            ) { requestId, callback ->
                service.health(requestId, callback)
            }
            if (!isConnectionAvailable) {
                ExtensionTransportHealth.UNAVAILABLE
            } else when (health) {
                "healthy" -> ExtensionTransportHealth.HEALTHY
                "degraded" -> ExtensionTransportHealth.DEGRADED
                else -> ExtensionTransportHealth.UNAVAILABLE
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            ExtensionTransportHealth.UNAVAILABLE
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            connectionState.clearUnavailableListener()
            connectionState.markUnavailable()
            invocations.close(CancellationException("Extension transport was closed"))
            resultDispatcher.close(CancellationException("Extension transport was closed"))
            binderScope.cancel()
            remoteCancelScope.cancel()
            runCatching { service.asBinder().unlinkToDeath(deathRecipient, 0) }
            runCatching { context.unbindService(connection) }
        }
    }

    private fun requestRemoteCancel(invocationId: InvocationId) {
        if (!remoteCancelPermits.tryAcquire()) return
        if (closed.get()) {
            try {
                if (service.asBinder().isBinderAlive) {
                    synchronized(binderCallLock) {
                        runCatching { service.cancel(invocationId.value) }
                    }
                }
            } finally {
                remoteCancelPermits.release()
            }
            return
        }
        remoteCancelScope.launch {
            try {
                if (service.asBinder().isBinderAlive) {
                    synchronized(binderCallLock) {
                        runCatching { service.cancel(invocationId.value) }
                    }
                }
            } finally {
                remoteCancelPermits.release()
            }
        }
    }

    private fun terminateInFlightOperations() {
        val failure = ExtensionTransportUnavailableException(
            "Extension service connection was lost"
        )
        invocations.fail(failure)
        resultDispatcher.close(failure)
        val internalCancellation =
            CancellationException("Extension service connection was lost").also { cancellation ->
                cancellation.initCause(failure)
            }
        binderScope.cancel(internalCancellation)
        remoteCancelScope.cancel(internalCancellation)
    }

    companion object {
        private const val MAX_MANIFEST_BYTES = 256 * 1024
        private const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
        private const val MAX_HEALTH_BYTES = 64
        private const val MAX_OUTSTANDING_BINDER_INVOCATIONS = 4
        private const val MAX_OUTSTANDING_BROKER_REQUESTS = 4
        private const val MAX_OUTSTANDING_REMOTE_CANCELS = 4
        private const val MAX_PROCESS_BINDER_INVOCATIONS = 16
        private val PROCESS_BINDER_INVOCATION_PERMITS =
            Semaphore(MAX_PROCESS_BINDER_INVOCATIONS, true)

        suspend fun connect(
            context: Context,
            installed: InstalledExtensionService,
            hostBridgeFactory: (
                ExtensionManifest,
                SerializedExtensionEnvelope,
            ) -> IExtensionHostBridge,
            hostApiVersion: ExtensionApiVersion = ExtensionApiVersions.Current,
            json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
        ): AndroidBoundExtensionTransport {
            val appContext = context.applicationContext
            return suspendCancellableCoroutine { continuation ->
                val connectionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                val connectionState = ExtensionConnectionState()
                val resultDispatcher = ExtensionResultDispatcher()
                val intent = Intent(ExtensionProtocol.SERVICE_ACTION).setComponent(
                    ComponentName(installed.packageName, installed.serviceName)
                )
                lateinit var connection: ServiceConnection
                connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                        connectionScope.launch {
                            if (!continuation.isActive) return@launch
                            runCatching {
                                val resolvedService =
                                    AndroidExtensionDiscovery(appContext).resolve(name)
                                requireCompatibleBoundServiceIdentity(
                                    expected = installed,
                                    connectedPackageName = name.packageName,
                                    connectedServiceName = name.className,
                                    resolved = resolvedService,
                                )
                                val service = IExtensionService.Stub.asInterface(binder)
                                val handshakePayload =
                                    ParcelFileCodec.write(
                                        appContext,
                                        json.encodeToString(
                                            ExtensionHandshakeRequest(
                                                transportVersion =
                                                    ExtensionProtocol.TRANSPORT_VERSION,
                                                hostApiVersion = hostApiVersion,
                                                supportedBrokerProtocolVersions =
                                                    BrokerProtocolVersions.Supported,
                                            )
                                        ),
                                    ).use { requestFile ->
                                        awaitControlResult(
                                            dispatcher = resultDispatcher,
                                            maximumBytes = MAX_MANIFEST_BYTES,
                                        ) { requestId, callback ->
                                            service.handshake(
                                                requestId,
                                                requestFile,
                                                callback,
                                            )
                                        }
                                    }
                                val handshake =
                                    decodeExtensionHandshake(handshakePayload, json)
                                requireCompatibleHandshake(handshake)
                                val manifestPayload = awaitControlResult(
                                    dispatcher = resultDispatcher,
                                    maximumBytes = MAX_MANIFEST_BYTES,
                                ) { requestId, callback ->
                                    service.openManifest(requestId, callback)
                                }
                                val manifest = decodeExtensionManifest(manifestPayload, json)
                                requireMatchingHandshakeManifest(handshake, manifest)
                                connectionState.markAvailable()
                                AndroidBoundExtensionTransport(
                                    appContext,
                                    connection,
                                    service,
                                    installed.uid,
                                    manifest,
                                    hostBridgeFactory,
                                    json,
                                    connectionState,
                                    resultDispatcher,
                                )
                            }.onSuccess { transport ->
                                if (!continuation.isActive) {
                                    transport.close()
                                } else {
                                    runCatching {
                                        continuation.resume(transport) { _, value, _ ->
                                            value.close()
                                        }
                                    }.onFailure {
                                        transport.close()
                                    }
                                }
                            }.onFailure { error ->
                                resultDispatcher.close(error)
                                runCatching { appContext.unbindService(connection) }
                                continuation.resumeFailure(error)
                            }
                            connectionScope.cancel()
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName) {
                        connectionState.markUnavailable()
                        failPendingConnection(
                            continuation = continuation,
                            connectionScope = connectionScope,
                            context = appContext,
                            connection = connection,
                            message = "Extension service disconnected while binding",
                        )
                    }

                    override fun onBindingDied(name: ComponentName) {
                        connectionState.markUnavailable()
                        failPendingConnection(
                            continuation = continuation,
                            connectionScope = connectionScope,
                            context = appContext,
                            connection = connection,
                            message = "Extension service binding died",
                        )
                    }

                    override fun onNullBinding(name: ComponentName) {
                        connectionState.markUnavailable()
                        failPendingConnection(
                            continuation = continuation,
                            connectionScope = connectionScope,
                            context = appContext,
                            connection = connection,
                            message = "Extension service returned a null binding",
                        )
                    }
                }
                if (!appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
                    connectionScope.cancel()
                    resultDispatcher.close()
                    continuation.resumeFailure(IllegalStateException("Unable to bind extension service"))
                }
                continuation.invokeOnCancellation {
                    connectionScope.cancel()
                    resultDispatcher.close()
                    runCatching { appContext.unbindService(connection) }
                }
            }
        }

        private fun failPendingConnection(
            continuation: CancellableContinuation<AndroidBoundExtensionTransport>,
            connectionScope: CoroutineScope,
            context: Context,
            connection: ServiceConnection,
            message: String,
        ) {
            connectionScope.cancel()
            runCatching { context.unbindService(connection) }
            continuation.resumeFailure(IllegalStateException(message))
        }
    }
}

private fun <T> CancellableContinuation<T>.resumeResult(result: Result<T>) {
    resumeWith(result)
}

private fun Throwable?.asInvocationCancellation(): CancellationException =
    this as? CancellationException
        ?: CancellationException("Extension invocation was cancelled").also { cancellation ->
            if (this != null) cancellation.initCause(this)
        }

internal class RevocableExtensionHostBridge(
    delegate: IExtensionHostBridge,
    private val expectedUid: Int,
    private val requestPermits: Semaphore,
) : IExtensionHostBridge.Stub(), Closeable {
    private val delegate = AtomicReference<IExtensionHostBridge?>(delegate)
    private val activeCallbacks = ConcurrentHashMap<String, PermitReleasingCallback>()

    override fun executeHttp(
        requestId: String?,
        request: ParcelFileDescriptor?,
        callback: IExtensionResultCallback?,
    ) {
        if (request == null) return
        if (requestId == null || callback == null || !requestId.isValidBrokerRequestId()) {
            runCatching { request.close() }
            return
        }
        if (Binder.getCallingUid() != expectedUid) {
            runCatching { request.close() }
            throw SecurityException("Extension host bridge caller does not match the connected service")
        }
        val current = delegate.get() ?: run {
            runCatching { request.close() }
            runCatching {
                callback.onFailure(
                    requestId,
                    "request.cancelled",
                    "Extension invocation is no longer active",
                )
            }
            return
        }
        if (!requestPermits.tryAcquire()) {
            runCatching { request.close() }
            runCatching {
                callback.onFailure(
                    requestId,
                    "request.overloaded",
                    "Extension has too many outstanding broker requests",
                )
            }
            return
        }
        if (!PROCESS_BROKER_REQUEST_PERMITS.tryAcquire()) {
            runCatching { request.close() }
            requestPermits.release()
            runCatching {
                callback.onFailure(
                    requestId,
                    "request.overloaded",
                    "Host broker is at capacity",
                )
            }
            return
        }
        lateinit var releasingCallback: PermitReleasingCallback
        releasingCallback = PermitReleasingCallback(requestId, callback) {
            activeCallbacks.remove(requestId, releasingCallback)
            PROCESS_BROKER_REQUEST_PERMITS.release()
            requestPermits.release()
        }
        if (activeCallbacks.putIfAbsent(requestId, releasingCallback) != null) {
            runCatching { request.close() }
            releasingCallback.fail("request.duplicate", "Broker request id is already active")
            return
        }
        if (delegate.get() !== current) {
            runCatching { request.close() }
            releasingCallback.fail("request.cancelled", "Extension invocation is no longer active")
            return
        }
        try {
            current.executeHttp(requestId, request, releasingCallback)
        } catch (failure: Exception) {
            runCatching { request.close() }
            releasingCallback.fail("request.failed", "Host broker request failed")
        }
    }

    override fun cancelHttp(requestId: String?) {
        if (requestId == null || !requestId.isValidBrokerRequestId()) return
        if (Binder.getCallingUid() != expectedUid) {
            throw SecurityException("Extension host bridge caller does not match the connected service")
        }
        activeCallbacks.remove(requestId)?.let { callback ->
            runCatching {
                callback.fail("request.cancelled", "Broker request was cancelled")
            }
        }
        delegate.get()?.let { current ->
            runCatching { current.cancelHttp(requestId) }
        }
    }

    override fun close() {
        val current = delegate.getAndSet(null)
        try {
            activeCallbacks.values.toList().forEach { callback ->
                runCatching {
                    callback.fail("request.cancelled", "Extension invocation is no longer active")
                }
            }
        } finally {
            (current as? Closeable)?.let { closeable -> runCatching { closeable.close() } }
        }
    }

    private class PermitReleasingCallback(
        private val requestId: String,
        private val delegate: IExtensionResultCallback,
        private val onComplete: () -> Unit,
    ) : IExtensionResultCallback.Stub() {
        private val completed = AtomicBoolean(false)

        override fun onSuccess(
            responseRequestId: String?,
            response: ParcelFileDescriptor?,
        ) {
            if (!completed.compareAndSet(false, true)) {
                response?.let { descriptor -> runCatching { descriptor.close() } }
                return
            }
            try {
                if (responseRequestId == requestId && response != null) {
                    delegate.onSuccess(requestId, response)
                } else {
                    response?.let { descriptor -> runCatching { descriptor.close() } }
                    delegate.onFailure(
                        requestId,
                        "request.invalid_response",
                        "Host broker returned a mismatched request id",
                    )
                }
            } finally {
                response?.let { descriptor -> runCatching { descriptor.close() } }
                onComplete()
            }
        }

        override fun onFailure(
            responseRequestId: String?,
            code: String?,
            message: String?,
        ) {
            if (!completed.compareAndSet(false, true)) return
            try {
                if (responseRequestId == requestId) {
                    delegate.onFailure(
                        requestId,
                        code ?: "request.failed",
                        message ?: "Host broker request failed",
                    )
                } else {
                    delegate.onFailure(
                        requestId,
                        "request.invalid_response",
                        "Host broker returned a mismatched request id",
                    )
                }
            } finally {
                onComplete()
            }
        }

        fun fail(code: String, message: String) = onFailure(requestId, code, message)
    }

    private fun String.isValidBrokerRequestId(): Boolean =
        isNotBlank() && length <= MAX_BROKER_REQUEST_ID_LENGTH

    private companion object {
        const val MAX_PROCESS_BROKER_REQUESTS = 16
        const val MAX_BROKER_REQUEST_ID_LENGTH = 64
        val PROCESS_BROKER_REQUEST_PERMITS = Semaphore(MAX_PROCESS_BROKER_REQUESTS, true)
    }
}

internal class ExtensionConnectionState {
    private val available = AtomicBoolean(false)
    private val unavailableListener = AtomicReference<(() -> Unit)?>(null)

    val isAvailable: Boolean
        get() = available.get()

    fun markAvailable() {
        available.set(true)
    }

    fun markUnavailable() {
        if (available.getAndSet(false)) unavailableListener.get()?.invoke()
    }

    fun setUnavailableListener(listener: () -> Unit) {
        unavailableListener.set(listener)
        if (!available.get() && unavailableListener.compareAndSet(listener, null)) listener()
    }

    fun clearUnavailableListener() {
        unavailableListener.set(null)
    }
}

private suspend fun awaitControlResult(
    dispatcher: ExtensionResultDispatcher,
    maximumBytes: Int,
    request: (requestId: String, callback: IExtensionResultCallback) -> Unit,
): String {
    val startedAt = SystemClock.elapsedRealtime()
    val response = withTimeout(CONTROL_RESPONSE_TIMEOUT_MILLIS) {
        dispatcher.await(request = request)
    }
    val elapsedMillis = SystemClock.elapsedRealtime() - startedAt
    val remainingMillis = CONTROL_RESPONSE_TIMEOUT_MILLIS - elapsedMillis
    if (remainingMillis <= 0L) {
        runCatching { response.close() }
        throw IOException("Extension control response timed out")
    }
    return ParcelFileCodec.readInterruptibly(
        descriptor = response,
        maximumBytes = maximumBytes,
        timeoutMillis = remainingMillis,
    )
}

private const val CONTROL_RESPONSE_TIMEOUT_MILLIS = 5_000L

internal fun matchesBoundServiceIdentity(
    expected: InstalledExtensionService,
    connectedPackageName: String,
    connectedServiceName: String,
    resolved: InstalledExtensionService?,
): Boolean =
    connectedPackageName == expected.packageName &&
    connectedServiceName == expected.serviceName &&
        resolved == expected

internal fun requireCompatibleBoundServiceIdentity(
    expected: InstalledExtensionService,
    connectedPackageName: String,
    connectedServiceName: String,
    resolved: InstalledExtensionService?,
) {
    if (
        !matchesBoundServiceIdentity(
            expected = expected,
            connectedPackageName = connectedPackageName,
            connectedServiceName = connectedServiceName,
            resolved = resolved,
        )
    ) {
        throw ExtensionTransportIncompatibleException(
            "Extension service identity or host binding permission changed while binding"
        )
    }
}

internal fun decodeExtensionHandshake(
    payload: String,
    json: Json,
): ExtensionHandshakeResponse = decodeExtensionWireValue(
    payload = payload,
    valueName = "handshake response",
    json = json,
)

internal fun decodeExtensionManifest(
    payload: String,
    json: Json,
): ExtensionManifest = decodeExtensionWireValue(
    payload = payload,
    valueName = "manifest",
    json = json,
)

internal fun requireCompatibleHandshake(handshake: ExtensionHandshakeResponse) {
    if (handshake.transportVersion != ExtensionProtocol.TRANSPORT_VERSION) {
        throw ExtensionTransportIncompatibleException(
            "Extension transport protocol is incompatible"
        )
    }
    handshake.error?.let { error ->
        throw ExtensionTransportIncompatibleException(
            "Extension handshake failed (${error.code}): ${error.message}"
        )
    }
    val brokerProtocolVersion = handshake.brokerProtocolVersion
        ?: throw ExtensionTransportIncompatibleException(
            "Extension handshake did not select a broker protocol"
        )
    if (brokerProtocolVersion !in BrokerProtocolVersions.Supported) {
        throw ExtensionTransportIncompatibleException(
            "Extension broker protocol is incompatible"
        )
    }
}

internal fun requireMatchingHandshakeManifest(
    handshake: ExtensionHandshakeResponse,
    manifest: ExtensionManifest,
) {
    if (handshake.extensionApiRange != manifest.apiRange) {
        throw ExtensionTransportIncompatibleException(
            "Extension handshake does not match its manifest"
        )
    }
}

private inline fun <reified T> decodeExtensionWireValue(
    payload: String,
    valueName: String,
    json: Json,
): T = try {
    payload.requireSafeExtensionJsonDepth()
    json.decodeFromString(payload)
} catch (error: Exception) {
    throw ExtensionTransportIncompatibleException(
        "Extension $valueName is malformed",
        error,
    )
}
