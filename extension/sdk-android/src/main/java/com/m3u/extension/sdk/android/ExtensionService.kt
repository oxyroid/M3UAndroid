package com.m3u.extension.sdk.android

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.InvocationId
import com.m3u.extension.api.SerializedExtensionEnvelope
import com.m3u.extension.api.SerializedExtensionResult
import com.m3u.extension.api.security.BrokerProtocolVersions
import com.m3u.extension.runtime.ExtensionTransport
import com.m3u.extension.transport.android.ExtensionHandshakeRequest
import com.m3u.extension.transport.android.ExtensionHandshakeError
import com.m3u.extension.transport.android.ExtensionHandshakeResponse
import com.m3u.extension.transport.android.ExtensionProtocol
import com.m3u.extension.transport.android.ParcelFileCodec
import com.m3u.extension.transport.android.ipc.IExtensionHostBridge
import com.m3u.extension.transport.android.ipc.IExtensionResultCallback
import com.m3u.extension.transport.android.ipc.IExtensionService
import java.io.Closeable
import java.util.LinkedHashSet
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

abstract class ExtensionService : Service() {
    protected abstract val transport: ExtensionTransport
    protected open val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    @Volatile
    private var negotiatedBrokerProtocolVersion: Int? = null
    private val handshakeLock = Any()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeInvocations = ConcurrentHashMap<String, Job>()
    private val invocationLock = Any()
    private val cancelledInvocationIds = LinkedHashSet<String>()

    /** Override when a hook needs the host broker. The default delegates to [transport]. */
    protected open suspend fun invoke(
        envelope: SerializedExtensionEnvelope,
        hostNetworkBroker: ExtensionHostNetworkBroker,
    ): SerializedExtensionResult = transport.invoke(envelope)

    private val binder = object : IExtensionService.Stub() {
        override fun handshake(
            requestId: String?,
            request: ParcelFileDescriptor?,
            callback: IExtensionResultCallback?,
        ) {
            val ownedRequest = request ?: return
            val safeRequestId = requestId?.takeIf { it.isValidWireRequestId() }
            if (safeRequestId == null || callback == null) {
                runCatching { ownedRequest.close() }
                return
            }
            respond(safeRequestId, callback, ownedRequest) {
                val handshake = json.decodeFromString<ExtensionHandshakeRequest>(
                    ParcelFileCodec.readInterruptibly(ownedRequest, MAX_HANDSHAKE_BYTES)
                )
                val extensionApiRange = transport.manifest.apiRange
                val brokerProtocolVersion = BrokerProtocolVersions.negotiate(
                    handshake.supportedBrokerProtocolVersions
                )
                val error = when {
                    handshake.transportVersion != ExtensionProtocol.TRANSPORT_VERSION ->
                        ExtensionHandshakeError(
                            code = "transport.incompatible",
                            message = "Extension transport protocol is incompatible",
                        )
                    !extensionApiRange.supportsHostApiMajor(
                        handshake.hostApiVersion.major
                    ) -> ExtensionHandshakeError(
                        code = "api.incompatible",
                        message = "Host API version is outside the extension range",
                    )
                    brokerProtocolVersion == null -> ExtensionHandshakeError(
                        code = "broker.incompatible",
                        message = "Host and extension do not share a broker protocol version",
                    )
                    else -> null
                }
                val response = if (error != null) {
                    ExtensionHandshakeResponse(
                        transportVersion = ExtensionProtocol.TRANSPORT_VERSION,
                        extensionApiRange = extensionApiRange,
                        error = error,
                    )
                } else {
                    checkNotNull(brokerProtocolVersion)
                    synchronized(handshakeLock) {
                        negotiatedBrokerProtocolVersion?.let { previous ->
                            require(previous == brokerProtocolVersion) {
                                "Broker protocol changed during an active extension connection"
                            }
                        }
                        negotiatedBrokerProtocolVersion = brokerProtocolVersion
                    }
                    ExtensionHandshakeResponse(
                        transportVersion = ExtensionProtocol.TRANSPORT_VERSION,
                        extensionApiRange = extensionApiRange,
                        brokerProtocolVersion = brokerProtocolVersion,
                    )
                }
                ParcelFileCodec.write(
                    this@ExtensionService,
                    json.encodeToString(response),
                )
            }
        }

        override fun openManifest(
            requestId: String?,
            callback: IExtensionResultCallback?,
        ) {
            val safeRequestId = requestId?.takeIf { it.isValidWireRequestId() } ?: return
            if (callback == null) return
            respond(safeRequestId, callback) {
                ParcelFileCodec.write(
                    this@ExtensionService,
                    json.encodeToString(transport.manifest),
                )
            }
        }

        override fun invoke(
            requestId: String?,
            invocationId: String?,
            hookId: String?,
            schemaVersion: Int,
            request: ParcelFileDescriptor?,
            hostBridge: IExtensionHostBridge?,
            callback: IExtensionResultCallback?,
        ) {
            val ownedRequest = request ?: return
            val safeRequestId = requestId?.takeIf { it.isValidWireRequestId() }
            if (safeRequestId == null || callback == null) {
                runCatching { ownedRequest.close() }
                return
            }
            val safeInvocationId = invocationId?.takeIf { it.isValidWireInvocationId() }
            val safeHookId = hookId?.takeIf { it.isValidWireHookId() }
            if (safeInvocationId == null || safeHookId == null || hostBridge == null) {
                fail(
                    safeRequestId,
                    callback,
                    "request.invalid",
                    "Extension request is invalid",
                )
                runCatching { ownedRequest.close() }
                return
            }
            lateinit var invocationJob: Job
            invocationJob = serviceScope.launch(start = CoroutineStart.LAZY) {
                respondNow(safeRequestId, callback) {
                    val envelope = json.decodeFromString<SerializedExtensionEnvelope>(
                        ParcelFileCodec.readInterruptibly(ownedRequest, MAX_REQUEST_BYTES)
                    )
                    require(envelope.invocationId.value == safeInvocationId)
                    require(envelope.hook.id == safeHookId)
                    require(envelope.schemaVersion == schemaVersion)
                    val broker = ExtensionHostNetworkBroker(
                        context = this@ExtensionService,
                        bridge = hostBridge,
                        json = json,
                        brokerProtocolVersion = checkNotNull(negotiatedBrokerProtocolVersion) {
                            "Extension handshake must complete before invocation"
                        },
                    )
                    try {
                        ParcelFileCodec.write(
                            this@ExtensionService,
                            json.encodeToString(
                                if (envelope.brokerScope == null) {
                                    transport.invoke(envelope)
                                } else {
                                    invoke(envelope, broker)
                                }
                            ),
                        )
                    } finally {
                        broker.close()
                    }
                }
            }
            val registration = synchronized(invocationLock) {
                when {
                    safeInvocationId in cancelledInvocationIds ->
                        InvocationRegistration.CANCELLED
                    activeInvocations.putIfAbsent(safeInvocationId, invocationJob) != null ->
                        InvocationRegistration.DUPLICATE
                    else -> InvocationRegistration.REGISTERED
                }
            }
            if (registration != InvocationRegistration.REGISTERED) {
                invocationJob.cancel()
                val (code, message) = when (registration) {
                    InvocationRegistration.CANCELLED ->
                        "request.cancelled" to "Invocation was cancelled before it started"
                    InvocationRegistration.DUPLICATE ->
                        "invocation.duplicate" to "Invocation id is already active"
                    InvocationRegistration.REGISTERED -> error("Registration already succeeded")
                }
                fail(safeRequestId, callback, code, message)
                runCatching { ownedRequest.close() }
                return
            }
            invocationJob.invokeOnCompletion {
                synchronized(invocationLock) {
                    activeInvocations.remove(safeInvocationId, invocationJob)
                }
                runCatching { ownedRequest.close() }
            }
            invocationJob.start()
        }

        override fun cancel(invocationId: String?) {
            val safeInvocationId =
                invocationId?.takeIf { it.isValidWireInvocationId() } ?: return
            val invocation = synchronized(invocationLock) {
                rememberCancelledInvocation(safeInvocationId)
                activeInvocations.remove(safeInvocationId)
            }
            invocation?.cancel(
                CancellationException("Invocation was cancelled by the host")
            )
            serviceScope.launch {
                runCatching { transport.cancel(InvocationId(safeInvocationId)) }
            }
        }

        override fun health(
            requestId: String?,
            callback: IExtensionResultCallback?,
        ) {
            val safeRequestId = requestId?.takeIf { it.isValidWireRequestId() } ?: return
            if (callback == null) return
            respond(safeRequestId, callback) {
                ParcelFileCodec.write(
                    this@ExtensionService,
                    transport.health().name.lowercase(),
                )
            }
        }
    }

    final override fun onBind(intent: Intent?): IBinder? =
        binder.takeIf { intent?.action == ExtensionProtocol.SERVICE_ACTION }

    override fun onDestroy() {
        synchronized(invocationLock) {
            activeInvocations.values.forEach { job -> job.cancel() }
            activeInvocations.clear()
            cancelledInvocationIds.clear()
        }
        serviceScope.cancel()
        (transport as? Closeable)?.let { closeable -> runCatching { closeable.close() } }
        super.onDestroy()
    }

    private fun respond(
        requestId: String,
        callback: IExtensionResultCallback,
        requestToClose: ParcelFileDescriptor? = null,
        block: suspend () -> ParcelFileDescriptor,
    ) {
        val responseJob = serviceScope.launch { respondNow(requestId, callback, block) }
        responseJob.invokeOnCompletion {
            requestToClose?.let { descriptor -> runCatching { descriptor.close() } }
        }
    }

    private suspend fun respondNow(
        requestId: String,
        callback: IExtensionResultCallback,
        block: suspend () -> ParcelFileDescriptor,
    ) {
        try {
            block().use { response -> callback.onSuccess(requestId, response) }
        } catch (cancellation: CancellationException) {
            fail(requestId, callback, "request.cancelled", "Extension request was cancelled")
        } catch (_: Exception) {
            fail(requestId, callback, "request.failed", "Extension request failed")
        }
    }

    private fun fail(
        requestId: String,
        callback: IExtensionResultCallback,
        code: String,
        message: String,
    ) {
        runCatching { callback.onFailure(requestId, code, message) }
    }

    private fun rememberCancelledInvocation(invocationId: String) {
        if (cancelledInvocationIds.size >= MAX_CANCELLED_INVOCATIONS) {
            val oldest = cancelledInvocationIds.iterator()
            if (oldest.hasNext()) {
                oldest.next()
                oldest.remove()
            }
        }
        cancelledInvocationIds += invocationId
    }

    private fun String.isValidWireRequestId(): Boolean =
        isNotBlank() && length <= MAX_REQUEST_ID_LENGTH

    private fun String.isValidWireInvocationId(): Boolean =
        isNotBlank() && length <= MAX_INVOCATION_ID_LENGTH

    private fun String.isValidWireHookId(): Boolean =
        isNotBlank() && length <= MAX_HOOK_ID_LENGTH

    private companion object {
        const val MAX_HANDSHAKE_BYTES = 16 * 1024
        const val MAX_REQUEST_BYTES = 4 * 1024 * 1024
        const val MAX_CANCELLED_INVOCATIONS = 256
        const val MAX_REQUEST_ID_LENGTH = 64
        const val MAX_INVOCATION_ID_LENGTH = 128
        const val MAX_HOOK_ID_LENGTH = 256
    }

    private enum class InvocationRegistration {
        REGISTERED,
        DUPLICATE,
        CANCELLED,
    }
}

internal fun ExtensionApiRange.supportsHostApiMajor(hostMajor: Int): Boolean =
    minimum.major == hostMajor && maximum.major == hostMajor
