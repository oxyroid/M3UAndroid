package com.m3u.extension.runtime

import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.InvocationId
import com.m3u.extension.api.SerializedExtensionEnvelope
import com.m3u.extension.api.SerializedExtensionResult

/**
 * Signals that the host refused to begin extension execution because its deadline was exhausted.
 *
 * This is a host admission outcome, not an extension failure.
 */
class HostInvocationDeadlineExceededException :
    RuntimeException("The host invocation deadline expired before extension execution")

interface ExtensionTransport {
    val manifest: ExtensionManifest

    suspend fun invoke(request: SerializedExtensionEnvelope): SerializedExtensionResult

    suspend fun cancel(invocationId: InvocationId)

    suspend fun health(): ExtensionTransportHealth
}

enum class ExtensionTransportHealth {
    HEALTHY,
    DEGRADED,
    UNAVAILABLE,
}
