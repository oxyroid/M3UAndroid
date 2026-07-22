package com.m3u.data.repository.plugin

import android.content.Context
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.Settings
import com.m3u.core.foundation.architecture.preferences.get
import com.m3u.data.extension.security.ExtensionHostBridge
import com.m3u.extension.api.security.HostNetworkBroker
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.runtime.ExtensionRegistrationResult
import com.m3u.extension.runtime.ExtensionRuntime
import com.m3u.extension.transport.android.AndroidBoundExtensionTransport
import com.m3u.extension.transport.android.AndroidExtensionDiscovery
import com.m3u.extension.transport.android.ExtensionTrustStore
import com.m3u.extension.transport.android.InstalledExtensionService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.withTimeout

internal class ExtensionPluginRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val discovery: AndroidExtensionDiscovery,
    private val trustStore: ExtensionTrustStore,
    private val hostNetworkBroker: HostNetworkBroker,
    private val runtime: ExtensionRuntime,
    private val settings: Settings,
) : ExtensionPluginRepository {
    private val transports = ConcurrentHashMap<String, AndroidBoundExtensionTransport>()

    override suspend fun installedPlugins(): List<InstalledPlugin> {
        if (!settings[PreferencesKeys.EXTERNAL_EXTENSIONS]) return emptyList()
        return discovery.discover().map { service ->
        val trusted = trustStore.isTrusted(service)
        val signatureChanged = trustStore.hasPinnedCertificate(service) && !trusted
        if (signatureChanged) {
            trustStore.setEnabled(service, false)
            trustStore.extensionId(service)?.let { extensionId ->
                runtime.unregister(ExtensionId(extensionId))
                transports.remove(extensionId)?.close()
            }
        }
        InstalledPlugin(
            packageName = service.packageName,
            serviceName = service.serviceName,
            certificateSha256 = service.certificateSha256,
            trusted = trusted,
            signatureChanged = signatureChanged,
            extensionId = trustStore.extensionId(service),
            enabled = trusted && trustStore.isEnabled(service),
            displayName = trustStore.displayName(service),
            version = trustStore.version(service),
            developer = trustStore.developer(service),
            grantedCapabilities = trustStore.extensionId(service)
                ?.let(trustStore::grantedCapabilities)
                .orEmpty(),
        )
        }
    }

    override suspend fun enable(packageName: String, serviceName: String): PluginEnableResult {
        if (!settings[PreferencesKeys.EXTERNAL_EXTENSIONS]) {
            return PluginEnableResult.Rejected("External extensions are disabled")
        }
        val service = discovery.discover().singleOrNull {
            it.packageName == packageName && it.serviceName == serviceName
        } ?: return PluginEnableResult.Rejected("Extension service is not installed")
        if (trustStore.hasPinnedCertificate(service) && !trustStore.isTrusted(service)) {
            return PluginEnableResult.Rejected("Extension signing certificate changed")
        }
        return runCatching {
            val transport = withTimeout(CONNECT_TIMEOUT_MILLIS) {
                AndroidBoundExtensionTransport.connect(
                    context = context,
                    installed = service,
                    hostBridgeFactory = { manifest ->
                        ExtensionHostBridge(context, hostNetworkBroker, manifest.id.value)
                    },
                )
            }
            when (val registration = runtime.register(transport)) {
                is ExtensionRegistrationResult.Registered -> {
                    trustStore.trust(
                        service = service,
                        extensionId = registration.extension.manifest.id.value,
                        capabilities = registration.extension.manifest.capabilities
                            .mapTo(mutableSetOf()) { it.capability.id },
                        displayName = registration.extension.manifest.displayName,
                        version = registration.extension.manifest.extensionVersion.toString(),
                        developer = registration.extension.manifest.metadata["developer"],
                    )
                    transports[registration.extension.manifest.id.value] = transport
                    PluginEnableResult.Enabled(registration.extension.manifest)
                }
                is ExtensionRegistrationResult.Rejected -> {
                    transport.close()
                    PluginEnableResult.Rejected(registration.error.message)
                }
            }
        }.getOrElse { error -> PluginEnableResult.Rejected(error.message ?: "Extension connection failed") }
    }

    override fun disable(extensionId: String): Boolean {
        val transport = transports.remove(extensionId)
        transport?.close()
        val removed = runtime.unregister(ExtensionId(extensionId)) != null
        val service = discovery.discover().firstOrNull { trustStore.extensionId(it) == extensionId }
        service?.let { trustStore.setEnabled(it, false) }
        return removed || transport != null || service != null
    }

    override fun revoke(packageName: String, serviceName: String) {
        discovery.discover().firstOrNull {
            it.packageName == packageName && it.serviceName == serviceName
        }?.let { service ->
            trustStore.extensionId(service)?.let(::disable)
            trustStore.revoke(service)
        }
    }

    override suspend fun restoreEnabled(): Int {
        if (!settings[PreferencesKeys.EXTERNAL_EXTENSIONS]) return 0
        var restored = 0
        discovery.discover()
            .filter { service -> trustStore.isTrusted(service) && trustStore.isEnabled(service) }
            .forEach { service ->
                if (enable(service.packageName, service.serviceName) is PluginEnableResult.Enabled) restored++
            }
        return restored
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 5_000L
    }
}
