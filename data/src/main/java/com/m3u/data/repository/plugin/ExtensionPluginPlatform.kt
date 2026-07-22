package com.m3u.data.repository.plugin

import android.content.Context
import com.m3u.data.extension.security.ExtensionHostBridge
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.InvocationId
import com.m3u.extension.api.SerializedExtensionEnvelope
import com.m3u.extension.api.SerializedExtensionResult
import com.m3u.extension.api.security.HostNetworkBroker
import com.m3u.extension.runtime.ExtensionTransport
import com.m3u.extension.runtime.ExtensionTransportHealth
import com.m3u.extension.transport.android.AndroidBoundExtensionTransport
import com.m3u.extension.transport.android.AndroidExtensionDiscovery
import com.m3u.extension.transport.android.InstalledExtensionService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

internal fun interface ExtensionPluginDiscovery {
    fun discover(): List<InstalledExtensionService>
}

internal class AndroidExtensionPluginDiscovery @Inject constructor(
    private val discovery: AndroidExtensionDiscovery,
) : ExtensionPluginDiscovery {
    override fun discover(): List<InstalledExtensionService> = discovery.discover()
}

internal interface ExtensionPluginTransport : ExtensionTransport {
    val isConnectionAvailable: Boolean
    fun close()
}

internal fun interface ExtensionPluginTransportConnector {
    suspend fun connect(service: InstalledExtensionService): ExtensionPluginTransport
}

internal class AndroidExtensionPluginTransportConnector @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val hostNetworkBroker: HostNetworkBroker,
) : ExtensionPluginTransportConnector {
    override suspend fun connect(service: InstalledExtensionService): ExtensionPluginTransport =
        AndroidExtensionPluginTransport(
            AndroidBoundExtensionTransport.connect(
                context = context,
                installed = service,
                hostBridgeFactory = { manifest, envelope ->
                    ExtensionHostBridge(context, hostNetworkBroker, manifest, envelope)
                },
            )
        )
}

private class AndroidExtensionPluginTransport(
    private val delegate: AndroidBoundExtensionTransport,
) : ExtensionPluginTransport {
    override val manifest: ExtensionManifest
        get() = delegate.manifest

    override val isConnectionAvailable: Boolean
        get() = delegate.isConnectionAvailable

    override suspend fun invoke(request: SerializedExtensionEnvelope): SerializedExtensionResult =
        delegate.invoke(request)

    override suspend fun cancel(invocationId: InvocationId) {
        delegate.cancel(invocationId)
    }

    override suspend fun health(): ExtensionTransportHealth = delegate.health()

    override fun close() {
        delegate.close()
    }
}
