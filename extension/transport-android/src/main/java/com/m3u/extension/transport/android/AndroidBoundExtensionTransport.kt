package com.m3u.extension.transport.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
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
import com.m3u.extension.transport.android.ipc.IExtensionService
import java.io.Closeable
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
) : ExtensionTransport, Closeable {
    private val closed = AtomicBoolean(false)
    private val binderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val remoteCancelScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val binderInvocationPermits = Semaphore(MAX_OUTSTANDING_BINDER_INVOCATIONS, true)
    private val brokerRequestPermits = Semaphore(MAX_OUTSTANDING_BROKER_REQUESTS, true)
    private val remoteCancelPermits = Semaphore(MAX_OUTSTANDING_REMOTE_CANCELS, true)
    private val invocations = ExtensionInvocationRegistry<SerializedExtensionResult>(::requestRemoteCancel)
    private val deathRecipient = IBinder.DeathRecipient(connectionState::markUnavailable)

    val isConnectionAvailable: Boolean
        get() = connectionState.isAvailable && !closed.get() && service.asBinder().isBinderAlive

    init {
        service.asBinder().linkToDeath(deathRecipient, 0)
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
                val result = runCatching {
                    try {
                        check(record.isActive) { "Extension invocation was cancelled" }
                        check(isConnectionAvailable) { "Extension service is unavailable" }
                        binderPermitAcquired = binderInvocationPermits.tryAcquire()
                        check(binderPermitAcquired) {
                            "Extension service has too many outstanding Binder invocations"
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
                            service.invoke(
                                request.invocationId.value,
                                request.hook.id,
                                request.schemaVersion,
                                requestFile,
                                bridge,
                            )
                        }
                        responseFile.use { response ->
                            check(record.isActive) { "Extension invocation was cancelled" }
                            check(isConnectionAvailable) { "Extension service is unavailable" }
                            json.decodeFromString<SerializedExtensionResult>(
                                ParcelFileCodec.read(response, MAX_RESPONSE_BYTES)
                            )
                        }
                    } finally {
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
        when (service.health()) {
            "healthy" -> ExtensionTransportHealth.HEALTHY
            "degraded" -> ExtensionTransportHealth.DEGRADED
            else -> ExtensionTransportHealth.UNAVAILABLE
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            connectionState.markUnavailable()
            invocations.close(CancellationException("Extension transport was closed"))
            binderScope.cancel()
            runCatching { service.asBinder().unlinkToDeath(deathRecipient, 0) }
            runCatching { context.unbindService(connection) }
        }
    }

    private fun requestRemoteCancel(invocationId: InvocationId) {
        if (!remoteCancelPermits.tryAcquire()) return
        remoteCancelScope.launch {
            try {
                if (service.asBinder().isBinderAlive) {
                    runCatching { service.cancel(invocationId.value) }
                }
            } finally {
                remoteCancelPermits.release()
            }
        }
    }

    companion object {
        private const val MAX_MANIFEST_BYTES = 256 * 1024
        private const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
        private const val MAX_OUTSTANDING_BINDER_INVOCATIONS = 4
        private const val MAX_OUTSTANDING_BROKER_REQUESTS = 4
        private const val MAX_OUTSTANDING_REMOTE_CANCELS = 4

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
                val intent = Intent(ExtensionProtocol.SERVICE_ACTION).setComponent(
                    ComponentName(installed.packageName, installed.serviceName)
                )
                lateinit var connection: ServiceConnection
                connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                        connectionScope.launch {
                            if (!continuation.isActive) return@launch
                            runCatching {
                                val service = IExtensionService.Stub.asInterface(binder)
                                val handshake = json.decodeFromString<ExtensionHandshakeResponse>(
                                    ParcelFileCodec.read(
                                        ParcelFileCodec.write(
                                            appContext,
                                            json.encodeToString(
                                                ExtensionHandshakeRequest(
                                                    transportVersion = ExtensionProtocol.TRANSPORT_VERSION,
                                                    hostApiVersion = hostApiVersion,
                                                    supportedBrokerProtocolVersions =
                                                        BrokerProtocolVersions.Supported,
                                                )
                                            )
                                        ).use { requestFile ->
                                            service.handshake(requestFile)
                                        },
                                        MAX_MANIFEST_BYTES,
                                    )
                                )
                                require(handshake.transportVersion == ExtensionProtocol.TRANSPORT_VERSION) {
                                    "Extension transport protocol is incompatible"
                                }
                                handshake.error?.let { error ->
                                    throw IllegalStateException(
                                        "Extension handshake failed (${error.code}): ${error.message}"
                                    )
                                }
                                val brokerProtocolVersion = checkNotNull(
                                    handshake.brokerProtocolVersion
                                ) { "Extension handshake did not select a broker protocol" }
                                require(
                                    brokerProtocolVersion in BrokerProtocolVersions.Supported
                                ) { "Extension broker protocol is incompatible" }
                                val manifest = json.decodeFromString<ExtensionManifest>(
                                    ParcelFileCodec.read(service.openManifest(), MAX_MANIFEST_BYTES)
                                )
                                require(handshake.extensionApiRange == manifest.apiRange) {
                                    "Extension handshake does not match its manifest"
                                }
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
                                )
                            }.onSuccess { transport ->
                                if (continuation.isActive) {
                                    continuation.resume(transport)
                                } else {
                                    transport.close()
                                }
                            }.onFailure { error ->
                                runCatching { appContext.unbindService(connection) }
                                if (continuation.isActive) continuation.resumeWithException(error)
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
                    continuation.resumeWithException(IllegalStateException("Unable to bind extension service"))
                }
                continuation.invokeOnCancellation {
                    connectionScope.cancel()
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
            if (continuation.isActive) {
                connectionScope.cancel()
                runCatching { context.unbindService(connection) }
                continuation.resumeWithException(IllegalStateException(message))
            }
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

    override fun executeHttp(request: ParcelFileDescriptor): ParcelFileDescriptor {
        if (Binder.getCallingUid() != expectedUid) {
            runCatching { request.close() }
            throw SecurityException("Extension host bridge caller does not match the connected service")
        }
        val current = delegate.get() ?: run {
            runCatching { request.close() }
            error("Extension invocation is no longer active")
        }
        if (!requestPermits.tryAcquire()) {
            runCatching { request.close() }
            error("Extension has too many outstanding broker requests")
        }
        return try {
            current.executeHttp(request)
        } finally {
            runCatching { request.close() }
            requestPermits.release()
        }
    }

    override fun close() {
        val current = delegate.getAndSet(null)
        (current as? Closeable)?.let { closeable -> runCatching { closeable.close() } }
    }
}

internal class ExtensionConnectionState {
    private val available = AtomicBoolean(false)

    val isAvailable: Boolean
        get() = available.get()

    fun markAvailable() {
        available.set(true)
    }

    fun markUnavailable() {
        available.set(false)
    }
}
