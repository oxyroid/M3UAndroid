package com.m3u.extension.runtime

import com.m3u.extension.api.Capability
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionPayload
import com.m3u.extension.api.ExtensionSettingsSnapshot
import com.m3u.extension.api.Hook
import com.m3u.extension.api.security.BrokerScopeHandle

data class ExtensionBrokerScopeRequest(
    val manifest: ExtensionManifest,
    val hook: Hook,
    val payload: ExtensionPayload,
    val settings: ExtensionSettingsSnapshot,
    val grantedCapabilities: Set<Capability>,
)

interface ExtensionBrokerScopeLease : AutoCloseable {
    val handle: BrokerScopeHandle

    override fun close()
}

fun interface ExtensionBrokerScopeProvider {
    /**
     * Opens a scope for one external invocation, or returns null when the Hook is not brokered.
     *
     * Implementations must bind the returned scope to the active extension principal and Hook.
     * [ExtensionRuntime] closes every returned lease after the invocation, including cancellation.
     */
    suspend fun open(request: ExtensionBrokerScopeRequest): ExtensionBrokerScopeLease?
}

object EmptyExtensionBrokerScopeProvider : ExtensionBrokerScopeProvider {
    override suspend fun open(request: ExtensionBrokerScopeRequest): ExtensionBrokerScopeLease? =
        null
}
