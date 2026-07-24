package com.m3u.data.extension.security

import android.content.Context
import android.os.ParcelFileDescriptor
import com.m3u.extension.api.Capability
import com.m3u.extension.api.ExtensionCapabilityIds
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
import com.m3u.extension.api.security.BrokeredHttpRequest
import com.m3u.extension.api.security.BrokeredHttpResponse
import com.m3u.extension.api.security.HostNetworkBrokerHooks
import com.m3u.extension.api.security.referencesCredential
import com.m3u.extension.transport.android.ParcelFileCodec
import com.m3u.extension.transport.android.ipc.IExtensionHostBridge
import com.m3u.extension.transport.android.ipc.IExtensionResultCallback
import com.m3u.extension.transport.android.requireSafeExtensionJsonDepth
import java.io.Closeable
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class ExtensionHostBridge(
    private val context: Context,
    private val broker: ProviderHostNetworkBroker,
    private val principal: ExtensionPrincipal,
    manifest: ExtensionManifest,
    envelope: SerializedExtensionEnvelope,
) : IExtensionHostBridge.Stub(), Closeable {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val active = AtomicBoolean(true)
    private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requestLock = Any()
    private val activeRequests = mutableMapOf<String, ActiveBrokerRequest>()
    private val hook = envelope.hook
    private val brokerScope = envelope.brokerScope
    private val grantedCapabilities = envelope.grantedCapabilities.toSet()
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
            val result: BrokerInvocationResult = try {
                BrokerInvocationResult.Success(executeOperation(invocationRequest = ownedRequest))
            } catch (failure: Exception) {
                val error = if (active.get() && !activeRequest.cancelled.get()) {
                    failure.toBrokerError()
                } else {
                    brokerError(BrokerErrorCodes.Cancelled, recoverable = true)
                }
                BrokerInvocationResult.Failure(error)
            }
            respond(safeRequestId, callback, result)
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
        val invocationPayload =
            ParcelFileCodec.readInterruptibly(invocationRequest, MAX_REQUEST_BYTES)
        invocationPayload.requireSafeExtensionJsonDepth()
        val invocation = json.decodeFromString<BrokerInvocation>(invocationPayload)
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
    }

    private fun respond(
        requestId: String,
        callback: IExtensionResultCallback,
        result: BrokerInvocationResult,
    ) {
        try {
            val encoded = encodeResponseWithinLimit(result)
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

    private fun encodeResponseWithinLimit(result: BrokerInvocationResult): String {
        val encoded = json.encodeToString(result)
        if (encoded.encodeToByteArray().size <= MAX_RESPONSE_ENVELOPE_BYTES) {
            return encoded
        }
        return json.encodeToString(
            BrokerInvocationResult.serializer(),
            BrokerInvocationResult.Failure(
                brokerError(BrokerErrorCodes.ResponseTooLarge, recoverable = false)
            )
        )
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
        is CancellationException -> brokerError(BrokerErrorCodes.Cancelled, recoverable = true)
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
    }
}
