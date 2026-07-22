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
import com.m3u.extension.api.security.referencesCredential
import com.m3u.extension.transport.android.ParcelFileCodec
import com.m3u.extension.transport.android.ipc.IExtensionHostBridge
import java.io.Closeable
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
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
    private val activeRequests = ConcurrentHashMap.newKeySet<Job>()
    private val hook = envelope.hook
    private val brokerScope = envelope.brokerScope
    private val grantedCapabilities = envelope.grantedCapabilities.toSet()

    init {
        require(envelope.extensionId == manifest.id) {
            "Invocation extension does not match the connected extension"
        }
        require(principal.extensionId == manifest.id) {
            "Connected Android service does not own the extension manifest"
        }
        val declaration = manifest.hooks.singleOrNull { candidate ->
            candidate.hook == envelope.hook
        } ?: error("Invocation hook is not declared by the connected extension")
        require(declaration.schemaVersion == envelope.schemaVersion) {
            "Invocation hook schema does not match the connected extension"
        }
        val declaredCapabilities = manifest.capabilities.mapTo(mutableSetOf()) { request ->
            request.capability
        }
        require(envelope.grantedCapabilities.all(declaredCapabilities::contains)) {
            "Invocation contains a capability not declared by the connected extension"
        }
    }

    override fun executeHttp(request: ParcelFileDescriptor): ParcelFileDescriptor = try {
        runBlocking {
            supervisorScope {
                val execution = async(start = CoroutineStart.LAZY) {
                    executeOperation(invocationRequest = request)
                }
                var registered = false
                val result: BrokerInvocationResult = try {
                    register(execution)
                    registered = true
                    execution.start()
                    BrokerInvocationResult.Success(execution.await())
                } catch (failure: Exception) {
                    BrokerInvocationResult.Failure(failure.toBrokerError())
                } finally {
                    if (registered) activeRequests.remove(execution)
                    execution.cancel()
                }
                ParcelFileCodec.write(context, json.encodeToString(result))
            }
        }
    } finally {
        runCatching { request.close() }
    }

    private suspend fun executeOperation(
        invocationRequest: ParcelFileDescriptor,
    ): BrokerOperationResult {
        val invocation = json.decodeFromString<BrokerInvocation>(
            ParcelFileCodec.read(invocationRequest, MAX_REQUEST_BYTES)
        )
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

    private fun requireBrokerScope() = brokerScope ?: throw ProviderBrokerException(
        BrokerErrorCodes.ScopeDenied,
        recoverable = false,
    )

    override fun close() {
        if (active.compareAndSet(true, false)) {
            val cancellation = CancellationException("Extension invocation is no longer active")
            activeRequests.forEach { job -> job.cancel(cancellation) }
            activeRequests.clear()
        }
    }

    private fun register(job: Job) {
        if (!active.get()) throw CancellationException("Extension invocation was cancelled")
        activeRequests += job
        if (!active.get()) {
            activeRequests.remove(job)
            val cancellation = CancellationException("Extension invocation was cancelled")
            job.cancel(cancellation)
            throw cancellation
        }
    }

    private suspend fun ensureInvocationActive() {
        currentCoroutineContext().ensureActive()
        if (!active.get()) throw CancellationException("Extension invocation was cancelled")
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

    private companion object {
        const val MAX_REQUEST_BYTES = 2 * 1024 * 1024
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
