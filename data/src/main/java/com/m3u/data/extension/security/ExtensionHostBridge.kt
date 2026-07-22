package com.m3u.data.extension.security

import android.content.Context
import android.os.ParcelFileDescriptor
import com.m3u.extension.api.Capability
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.SerializedExtensionEnvelope
import com.m3u.extension.api.security.BrokerInvocation
import com.m3u.extension.api.security.BrokeredHttpRequest
import com.m3u.extension.api.security.BrokerValue
import com.m3u.extension.api.security.HostNetworkBroker
import com.m3u.extension.transport.android.ParcelFileCodec
import com.m3u.extension.transport.android.ipc.IExtensionHostBridge
import java.io.Closeable
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class ExtensionHostBridge(
    private val context: Context,
    private val broker: HostNetworkBroker,
    manifest: ExtensionManifest,
    envelope: SerializedExtensionEnvelope,
) : IExtensionHostBridge.Stub(), Closeable {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val active = AtomicBoolean(true)
    private val activeRequests = ConcurrentHashMap.newKeySet<Job>()
    private val extensionId = manifest.id.value
    private val invocationId = envelope.invocationId
    private val hook = envelope.hook
    private val grantedCapabilities = envelope.grantedCapabilities.toSet()

    init {
        require(envelope.extensionId == manifest.id) {
            "Invocation extension does not match the connected extension"
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

    override fun executeHttp(request: ParcelFileDescriptor): ParcelFileDescriptor = runBlocking {
        val job = checkNotNull(currentCoroutineContext()[Job])
        var registered = false
        try {
            register(job)
            registered = true
            val invocation = json.decodeFromString<BrokerInvocation>(
                ParcelFileCodec.read(request, MAX_REQUEST_BYTES)
            )
            requireCapability(ExtensionCapabilityIds.Network)
            if (invocation.request.usesCredential()) {
                requireCapability(ExtensionCapabilityIds.CredentialRead)
            }
            if (invocation.request.secretCapture != null) {
                requireCapability(ExtensionCapabilityIds.CredentialWrite)
            }
            currentCoroutineContext().ensureActive()
            check(active.get()) { "Extension invocation is no longer active" }
            val response = broker.execute(
                extensionId = extensionId,
                accountId = invocation.accountId,
                request = invocation.request,
            )
            currentCoroutineContext().ensureActive()
            check(active.get()) { "Extension invocation is no longer active" }
            ParcelFileCodec.write(context, json.encodeToString(response))
        } finally {
            if (registered) activeRequests.remove(job)
            runCatching { request.close() }
        }
    }

    override fun close() {
        if (active.compareAndSet(true, false)) {
            val cancellation = CancellationException("Extension invocation is no longer active")
            activeRequests.forEach { job -> job.cancel(cancellation) }
            activeRequests.clear()
        }
    }

    private fun register(job: Job) {
        check(active.get()) { "Extension invocation is no longer active" }
        activeRequests += job
        if (!active.get()) {
            activeRequests.remove(job)
            job.cancel(CancellationException("Extension invocation is no longer active"))
            error("Extension invocation is no longer active")
        }
    }

    private fun requireCapability(capability: Capability) {
        if (capability !in grantedCapabilities) {
            throw SecurityException(
                "Invocation $invocationId for $hook is not granted ${capability.id}"
            )
        }
    }

    private fun BrokeredHttpRequest.usesCredential(): Boolean =
        headers.values.any { value -> value is BrokerValue.Secret } ||
            body.any { value -> value is BrokerValue.Secret }

    private companion object {
        const val MAX_REQUEST_BYTES = 2 * 1024 * 1024
    }
}
