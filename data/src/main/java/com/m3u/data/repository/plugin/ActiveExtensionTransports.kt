package com.m3u.data.repository.plugin

import com.m3u.extension.transport.android.InstalledExtensionService
import java.util.concurrent.ConcurrentHashMap

internal data class ExtensionServiceKey(
    val packageName: String,
    val serviceName: String,
)

internal data class ActiveExtensionTransport<Transport>(
    val serviceKey: ExtensionServiceKey,
    val extensionId: String,
    val transport: Transport,
)

internal class ActiveExtensionTransports<Transport> {
    private val entries =
        ConcurrentHashMap<ExtensionServiceKey, ActiveExtensionTransport<Transport>>()

    operator fun get(key: ExtensionServiceKey): ActiveExtensionTransport<Transport>? = entries[key]

    fun put(
        key: ExtensionServiceKey,
        extensionId: String,
        transport: Transport,
    ): ActiveExtensionTransport<Transport>? = entries.put(
        key,
        ActiveExtensionTransport(key, extensionId, transport),
    )

    fun remove(key: ExtensionServiceKey): ActiveExtensionTransport<Transport>? = entries.remove(key)

    fun removeByExtensionId(extensionId: String): List<ActiveExtensionTransport<Transport>> =
        removeMatching { active -> active.extensionId == extensionId }

    fun removeMissing(
        installed: Set<ExtensionServiceKey>,
    ): List<ActiveExtensionTransport<Transport>> =
        removeMatching { active -> active.serviceKey !in installed }

    fun removeAll(): List<ActiveExtensionTransport<Transport>> = removeMatching { true }

    private fun removeMatching(
        predicate: (ActiveExtensionTransport<Transport>) -> Boolean,
    ): List<ActiveExtensionTransport<Transport>> = entries.entries
        .mapNotNull { (key, active) ->
            active.takeIf(predicate)?.takeIf { entries.remove(key, active) }
        }
}

internal val InstalledExtensionService.key: ExtensionServiceKey
    get() = ExtensionServiceKey(packageName, serviceName)
