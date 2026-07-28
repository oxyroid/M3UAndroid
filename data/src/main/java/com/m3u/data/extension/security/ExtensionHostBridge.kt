package com.m3u.data.extension.security

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import com.m3u.extension.api.Capability
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionInvocationBudget
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.SerializedExtensionEnvelope
import com.m3u.extension.api.security.BrokerAuthenticationRequest
import com.m3u.extension.api.security.BrokerAuthenticationResponse
import com.m3u.extension.api.security.BrokerErrorCode
import com.m3u.extension.api.security.BrokerErrorCodes
import com.m3u.extension.api.security.BrokerInvocation
import com.m3u.extension.api.security.BrokerInvocationError
import com.m3u.extension.api.security.BrokerInvocationResult
import com.m3u.extension.api.security.BrokerHttpExchange
import com.m3u.extension.api.security.BrokerOperation
import com.m3u.extension.api.security.BrokerOperationResult
import com.m3u.extension.api.security.BrokerProtocolVersions
import com.m3u.extension.api.security.BrokeredHttpRequest
import com.m3u.extension.api.security.BrokeredHttpResponse
import com.m3u.extension.api.security.HostNetworkBrokerHooks
import com.m3u.extension.api.security.referencesCredential
import com.m3u.extension.transport.android.ParcelFileCodec
import com.m3u.extension.transport.android.ipc.IExtensionHostBridge
import com.m3u.extension.transport.android.ipc.IExtensionResultCallback
import com.m3u.extension.transport.android.requireSafeExtensionJsonDepth
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class ExtensionHostBridge(
    private val context: Context,
    private val broker: ProviderHostNetworkBroker,
    private val principal: ExtensionPrincipal,
    manifest: ExtensionManifest,
    envelope: SerializedExtensionEnvelope,
    private val brokerProtocolVersion: Int,
    private val elapsedRealtimeMillis: () -> Long = SystemClock::elapsedRealtime,
) : IExtensionHostBridge.Stub(), Closeable {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val active = AtomicBoolean(true)
    private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requestLock = Any()
    private val activeRequests = mutableMapOf<String, ActiveBrokerRequest>()
    // Keep file reads and byte accounting in one critical section so concurrent requests cannot
    // each observe the same remaining invocation budget.
    private val requestReadMutex = Mutex()
    private val budgetLock = Any()
    private val hook = envelope.hook
    private val brokerScope = envelope.brokerScope
    private val grantedCapabilities = envelope.grantedCapabilities.toSet()
    private val invocationBudget = envelope.invocationBudget ?: LEGACY_INVOCATION_BUDGET
    private val responseTooLargeEnvelope = json.encodeToString(
        BrokerInvocationResult.serializer(),
        BrokerInvocationResult.Failure(
            brokerError(BrokerErrorCodes.ResponseTooLarge, recoverable = false)
        ),
    )
    private val responseTooLargeEnvelopeBytes =
        responseTooLargeEnvelope.encodeToByteArray().size.toLong()
    private val invocationDeadlineMillis = deadlineAfter(
        startMillis = elapsedRealtimeMillis(),
        durationMillis = invocationBudget.remainingTimeMillis,
    )
    private var remainingBrokerRequests = invocationBudget.maxBrokerRequests
    private var remainingBrokerRequestBytes = invocationBudget.maxBrokerRequestBytes
    // Reserve one typed terminal envelope up front. It can therefore be emitted without exceeding
    // the cumulative encoded-response budget; later over-budget calls use the Binder failure path.
    private var responseTooLargeEnvelopeAvailable =
        invocationBudget.maxBrokerResponseBytes >= responseTooLargeEnvelopeBytes
    private var remainingBrokerResponseBytes = invocationBudget.maxBrokerResponseBytes -
        if (responseTooLargeEnvelopeAvailable) responseTooLargeEnvelopeBytes else 0L
    private val hookDeclaration = manifest.hooks.singleOrNull { candidate ->
        candidate.hook == envelope.hook
    } ?: error("Invocation hook is not declared by the connected extension")

    init {
        require(envelope.extensionId == manifest.id) {
            "Invocation extension does not match the connected extension"
        }
        require(principal.extensionId == manifest.id) {
            "Connected Android service does not own the extension manifest"
        }
        require(hookDeclaration.schemaVersion == envelope.schemaVersion) {
            "Invocation hook schema does not match the connected extension"
        }
        require(brokerProtocolVersion in BrokerProtocolVersions.Supported) {
            "Invocation broker protocol was not negotiated by the host"
        }
        val declaredCapabilities = manifest.capabilities.mapTo(mutableSetOf()) { request ->
            request.capability
        }
        require(envelope.grantedCapabilities.all(declaredCapabilities::contains)) {
            "Invocation contains a capability not declared by the connected extension"
        }
    }

    override fun executeHttp(
        requestId: String?,
        request: ParcelFileDescriptor?,
        callback: IExtensionResultCallback?,
    ) {
        val ownedRequest = request ?: return
        val safeRequestId = requestId?.takeIf { it.isValidBrokerRequestId() }
        if (safeRequestId == null || callback == null) {
            runCatching { ownedRequest.close() }
            return
        }
        lateinit var activeRequest: ActiveBrokerRequest
        val execution = bridgeScope.launch(start = CoroutineStart.LAZY) {
            try {
                withInvocationDeadline {
                    val result = BrokerInvocationResult.Success(
                        executeOperation(invocationRequest = ownedRequest)
                    )
                    ensureInvocationActive()
                    respond(safeRequestId, callback, result)
                }
            } catch (failure: Exception) {
                val error = when {
                    !active.get() || activeRequest.cancelled.get() ->
                        brokerError(BrokerErrorCodes.Cancelled, recoverable = true)
                    remainingInvocationTimeMillis() <= 0 ->
                        brokerError(BrokerErrorCodes.Timeout, recoverable = true)
                    else -> failure.toBrokerError()
                }
                respond(
                    safeRequestId,
                    callback,
                    BrokerInvocationResult.Failure(error),
                )
            }
        }
        activeRequest = ActiveBrokerRequest(
            job = execution,
            descriptor = ownedRequest,
        )
        val registration = synchronized(requestLock) {
            when {
                !active.get() -> BrokerRequestRegistration.CLOSED
                safeRequestId in activeRequests ->
                    BrokerRequestRegistration.DUPLICATE
                activeRequests.size >= MAX_ACTIVE_BROKER_REQUESTS ->
                    BrokerRequestRegistration.LIMIT_EXCEEDED
                else -> {
                    activeRequests[safeRequestId] = activeRequest
                    BrokerRequestRegistration.REGISTERED
                }
            }
        }
        if (registration != BrokerRequestRegistration.REGISTERED) {
            execution.cancel()
            runCatching { ownedRequest.close() }
            respond(
                safeRequestId,
                callback,
                BrokerInvocationResult.Failure(
                    when (registration) {
                        BrokerRequestRegistration.CLOSED ->
                            brokerError(BrokerErrorCodes.Cancelled, recoverable = true)
                        BrokerRequestRegistration.DUPLICATE ->
                            brokerError(BrokerErrorCodes.InvalidRequest, recoverable = false)
                        BrokerRequestRegistration.LIMIT_EXCEEDED ->
                            brokerError(BrokerErrorCodes.InvalidRequest, recoverable = false)
                        BrokerRequestRegistration.REGISTERED ->
                            error("Broker request registration already succeeded")
                    }
                ),
            )
            return
        }
        execution.invokeOnCompletion {
            synchronized(requestLock) {
                activeRequests.remove(safeRequestId, activeRequest)
            }
            runCatching { ownedRequest.close() }
        }
        execution.start()
    }

    override fun cancelHttp(requestId: String?) {
        val safeRequestId = requestId?.takeIf { it.isValidBrokerRequestId() } ?: return
        val request = synchronized(requestLock) {
            activeRequests.remove(safeRequestId)
        } ?: return
        request.cancelled.set(true)
        runCatching { request.descriptor.close() }
        request.job.cancel(CancellationException("Broker request was cancelled by the extension"))
    }

    private suspend fun executeOperation(
        invocationRequest: ParcelFileDescriptor,
    ): BrokerOperationResult {
        ensureInvocationActive()
        requestReadMutex.lock()
        val invocationPayload = try {
            val requestReservation = reserveBrokerRequest()
            ParcelFileCodec.readInterruptiblyWithEncodedSize(
                descriptor = invocationRequest,
                maximumBytes = requestReservation.maximumBytes,
            ).also { payload ->
                refundUnusedBrokerRequestBytes(
                    requestReservation.maximumBytes - payload.encodedByteCount
                )
            }
        } finally {
            requestReadMutex.unlock()
        }
        invocationPayload.content.requireSafeExtensionJsonDepth()
        val invocation = json.decodeFromString<BrokerInvocation>(invocationPayload.content)
        if (invocation.brokerProtocolVersion != brokerProtocolVersion) {
            throw ProviderBrokerException(
                BrokerErrorCodes.InvalidRequest,
                recoverable = false,
            )
        }
        ensureInvocationActive()
        return when (val operation = invocation.operation) {
            is BrokerOperation.Http -> BrokerOperationResult.Http(
                executeHttp(operation.request)
            )
            is BrokerOperation.Authenticate -> BrokerOperationResult.Authentication(
                authenticate(operation.request)
            )
        }
    }

    private suspend fun executeHttp(request: BrokeredHttpRequest): BrokeredHttpResponse {
        requireBrokerSupportedHook()
        requireCapability(ExtensionCapabilityIds.Network)
        if (request.usesCredential()) {
            requireCapability(ExtensionCapabilityIds.CredentialRead)
        }
        ensureInvocationActive()
        val scope = requireBrokerScope()
        val response = broker.execute(
            scope = scope,
            principal = principal,
            hook = hook,
            request = request,
        )
        ensureInvocationActive()
        return response
    }

    private suspend fun authenticate(
        request: BrokerAuthenticationRequest,
    ): BrokerAuthenticationResponse {
        requireBrokerSupportedHook()
        requireCapability(ExtensionCapabilityIds.Network)
        requireCapability(ExtensionCapabilityIds.CredentialWrite)
        if (request.exchange.url.referencesCredential()) {
            throw ProviderBrokerException(
                BrokerErrorCodes.InvalidRequest,
                recoverable = false,
            )
        }
        if (request.exchange.usesCredential()) {
            requireCapability(ExtensionCapabilityIds.CredentialRead)
        }
        ensureInvocationActive()
        val response = broker.authenticate(
            scope = requireBrokerScope(),
            principal = principal,
            hook = hook,
            request = request,
        )
        ensureInvocationActive()
        return response
    }

    private fun requireBrokerSupportedHook() {
        if (
            !HostNetworkBrokerHooks.supports(hook) ||
            ExtensionCapabilityIds.Network !in hookDeclaration.requiredCapabilities
        ) {
            throw ProviderBrokerException(
                BrokerErrorCodes.ScopeDenied,
                recoverable = false,
            )
        }
    }

    private fun requireBrokerScope() = brokerScope ?: throw ProviderBrokerException(
        BrokerErrorCodes.ScopeDenied,
        recoverable = false,
    )

    override fun close() {
        if (active.compareAndSet(true, false)) {
            val cancellation = CancellationException("Extension invocation is no longer active")
            val requests = synchronized(requestLock) {
                activeRequests.values.toList().also { activeRequests.clear() }
            }
            requests.forEach { request ->
                request.cancelled.set(true)
                runCatching { request.descriptor.close() }
                request.job.cancel(cancellation)
            }
            bridgeScope.cancel(cancellation)
        }
    }

    private suspend fun ensureInvocationActive() {
        currentCoroutineContext().ensureActive()
        if (!active.get()) throw CancellationException("Extension invocation was cancelled")
        if (remainingInvocationTimeMillis() <= 0) {
            throw ProviderBrokerException(
                BrokerErrorCodes.Timeout,
                recoverable = true,
            )
        }
    }

    private fun respond(
        requestId: String,
        callback: IExtensionResultCallback,
        result: BrokerInvocationResult,
    ) {
        try {
            val encoded = encodeResponseWithinLimit(result) ?: run {
                callback.onFailure(
                    requestId,
                    BrokerErrorCodes.ResponseTooLarge.value,
                    SAFE_ERROR_MESSAGES.getValue(BrokerErrorCodes.ResponseTooLarge),
                )
                return
            }
            ParcelFileCodec.write(
                context = context,
                content = encoded,
                maximumBytes = MAX_RESPONSE_ENVELOPE_BYTES,
            ).use { response ->
                callback.onSuccess(requestId, response)
            }
        } catch (_: Exception) {
            runCatching {
                callback.onFailure(requestId, "broker.failed", "Host broker request failed")
            }
        }
    }

    private fun encodeResponseWithinLimit(result: BrokerInvocationResult): String? {
        val encoded = json.encodeToString(result)
        val encodedBytes = encoded.encodeToByteArray().size
        val withinFixedLimit = encodedBytes <= MAX_RESPONSE_ENVELOPE_BYTES
        val withinInvocationBudget = withinFixedLimit &&
            reserveBrokerResponseBytes(encodedBytes)
        if (withinInvocationBudget) {
            return encoded
        }
        return takeResponseTooLargeEnvelope()
    }

    private suspend fun <T> withInvocationDeadline(block: suspend () -> T): T {
        val remainingMillis = remainingInvocationTimeMillis()
        if (remainingMillis <= 0) {
            throw ProviderBrokerException(
                BrokerErrorCodes.Timeout,
                recoverable = true,
            )
        }
        return try {
            withTimeout(remainingMillis) {
                block()
            }
        } catch (failure: TimeoutCancellationException) {
            throw ProviderBrokerException(
                BrokerErrorCodes.Timeout,
                recoverable = true,
                cause = failure,
            )
        }
    }

    private fun remainingInvocationTimeMillis(): Long {
        val nowMillis = elapsedRealtimeMillis()
        return if (nowMillis >= invocationDeadlineMillis) {
            0
        } else {
            invocationDeadlineMillis - nowMillis
        }
    }

    private fun reserveBrokerRequest(): BrokerRequestReservation = synchronized(budgetLock) {
        if (remainingBrokerRequests <= 0 || remainingBrokerRequestBytes <= 0) {
            throw ProviderBrokerException(
                BrokerErrorCodes.InvalidRequest,
                recoverable = false,
            )
        }
        remainingBrokerRequests--
        val maximumBytes = minOf(
            MAX_REQUEST_BYTES.toLong(),
            remainingBrokerRequestBytes,
        ).toInt()
        remainingBrokerRequestBytes -= maximumBytes
        BrokerRequestReservation(maximumBytes)
    }

    private fun refundUnusedBrokerRequestBytes(unusedBytes: Int) = synchronized(budgetLock) {
        check(unusedBytes >= 0) { "Broker request exceeded its reserved byte budget" }
        remainingBrokerRequestBytes += unusedBytes
    }

    private fun reserveBrokerResponseBytes(encodedBytes: Int): Boolean =
        synchronized(budgetLock) {
            val encodedByteCount = encodedBytes.toLong()
            if (encodedByteCount > remainingBrokerResponseBytes) {
                false
            } else {
                remainingBrokerResponseBytes -= encodedByteCount
                true
            }
        }

    private fun takeResponseTooLargeEnvelope(): String? = synchronized(budgetLock) {
        if (!responseTooLargeEnvelopeAvailable) {
            null
        } else {
            responseTooLargeEnvelopeAvailable = false
            responseTooLargeEnvelope
        }
    }

    private fun requireCapability(capability: Capability) {
        if (capability !in grantedCapabilities) {
            throw ProviderBrokerException(
                BrokerErrorCodes.CapabilityDenied,
                recoverable = true,
            )
        }
    }

    private fun Exception.toBrokerError(): BrokerInvocationError = when (this) {
        is ProviderBrokerException -> brokerError(code, recoverable)
        is TimeoutCancellationException ->
            brokerError(BrokerErrorCodes.Timeout, recoverable = true)
        is CancellationException -> brokerError(BrokerErrorCodes.Cancelled, recoverable = true)
        is IOException -> if (cause is TimeoutException) {
            brokerError(BrokerErrorCodes.Timeout, recoverable = true)
        } else {
            brokerError(BrokerErrorCodes.Internal, recoverable = true)
        }
        is SecurityException -> brokerError(BrokerErrorCodes.ScopeDenied, recoverable = false)
        is IllegalArgumentException -> brokerError(
            BrokerErrorCodes.InvalidRequest,
            recoverable = false,
        )
        else -> brokerError(BrokerErrorCodes.Internal, recoverable = true)
    }

    private fun brokerError(
        code: BrokerErrorCode,
        recoverable: Boolean,
    ) = BrokerInvocationError(
        code = code,
        recoverable = recoverable,
        message = SAFE_ERROR_MESSAGES.getValue(code),
    )

    private fun BrokeredHttpRequest.usesCredential(): Boolean =
        headers.values.any { value -> value.referencesCredential() } ||
            body.any { value -> value.referencesCredential() } ||
            url.referencesCredential()

    private fun BrokerHttpExchange.usesCredential(): Boolean =
        headers.values.any { value -> value.referencesCredential() } ||
            body.any { value -> value.referencesCredential() } ||
            url.referencesCredential()

    private fun String.isValidBrokerRequestId(): Boolean =
        isNotBlank() && length <= MAX_BROKER_REQUEST_ID_LENGTH

    private data class ActiveBrokerRequest(
        val job: Job,
        val descriptor: ParcelFileDescriptor,
        val cancelled: AtomicBoolean = AtomicBoolean(false),
    )

    private data class BrokerRequestReservation(
        val maximumBytes: Int,
    )

    private enum class BrokerRequestRegistration {
        REGISTERED,
        DUPLICATE,
        LIMIT_EXCEEDED,
        CLOSED,
    }

    private companion object {
        const val MAX_REQUEST_BYTES = 2 * 1024 * 1024
        const val MAX_RESPONSE_ENVELOPE_BYTES = 5 * 1024 * 1024
        const val MAX_BROKER_REQUEST_ID_LENGTH = 64
        const val MAX_ACTIVE_BROKER_REQUESTS = 4
        val LEGACY_INVOCATION_BUDGET = ExtensionInvocationBudget(
            remainingTimeMillis = 30_000,
            maxBrokerRequests = 16,
            maxBrokerRequestBytes = 4L * 1024 * 1024,
            maxBrokerResponseBytes = 16L * 1024 * 1024,
        )
        val SAFE_ERROR_MESSAGES = mapOf(
            BrokerErrorCodes.InvalidRequest to "The broker request is invalid",
            BrokerErrorCodes.CapabilityDenied to "The broker capability is not granted",
            BrokerErrorCodes.ScopeDenied to "The broker scope does not authorize this request",
            BrokerErrorCodes.Timeout to "The broker request timed out",
            BrokerErrorCodes.Cancelled to "The broker request was cancelled",
            BrokerErrorCodes.NetworkFailed to "The broker network request failed",
            BrokerErrorCodes.ResponseTooLarge to "The broker response exceeded the allowed size",
            BrokerErrorCodes.Internal to "The broker request failed",
        )

        fun deadlineAfter(
            startMillis: Long,
            durationMillis: Long,
        ): Long = if (startMillis > Long.MAX_VALUE - durationMillis) {
            Long.MAX_VALUE
        } else {
            startMillis + durationMillis
        }

    }
}
