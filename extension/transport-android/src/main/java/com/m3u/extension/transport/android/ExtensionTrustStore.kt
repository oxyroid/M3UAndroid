package com.m3u.extension.transport.android

import android.content.Context

class ExtensionTrustStore(context: Context) {
    private val preferences = context.getSharedPreferences("extension-trust", Context.MODE_PRIVATE)

    fun trust(
        service: InstalledExtensionService,
        extensionId: String,
        capabilities: Set<String>,
        displayName: String,
        version: String,
        developer: String?,
    ) {
        preferences.edit()
            .putString("${service.key}:certificate", service.certificateSha256)
            .putString("${service.key}:extension-id", extensionId)
            .putBoolean("${service.key}:enabled", true)
            .putStringSet("${service.key}:capabilities", capabilities)
            .putString("${service.key}:display-name", displayName)
            .putString("${service.key}:version", version)
            .putString("${service.key}:developer", developer)
            .apply()
    }

    fun updateTrustedManifest(
        service: InstalledExtensionService,
        extensionId: String,
        capabilities: Set<String>,
        displayName: String,
        version: String,
        developer: String?,
    ) {
        check(isTrusted(service)) { "Extension identity is not trusted" }
        check(extensionId(service) == extensionId) { "Extension identity cannot change during reconnect" }
        preferences.edit()
            .putString("${service.key}:extension-id", extensionId)
            .putStringSet("${service.key}:capabilities", capabilities)
            .putString("${service.key}:display-name", displayName)
            .putString("${service.key}:version", version)
            .putString("${service.key}:developer", developer)
            .apply()
    }

    fun revoke(service: InstalledExtensionService) {
        revoke(service.packageName, service.serviceName)
    }

    fun revoke(packageName: String, serviceName: String) {
        val serviceKey = serviceKey(packageName, serviceName)
        preferences.edit()
            .remove("$serviceKey$CERTIFICATE_SUFFIX")
            .remove("$serviceKey$EXTENSION_ID_SUFFIX")
            .remove("$serviceKey$ENABLED_SUFFIX")
            .remove("$serviceKey$CAPABILITIES_SUFFIX")
            .remove("$serviceKey$DISPLAY_NAME_SUFFIX")
            .remove("$serviceKey$VERSION_SUFFIX")
            .remove("$serviceKey$DEVELOPER_SUFFIX")
            .apply()
    }

    fun isTrusted(service: InstalledExtensionService): Boolean =
        preferences.getString("${service.key}:certificate", null) == service.certificateSha256

    fun hasPinnedCertificate(service: InstalledExtensionService): Boolean =
        preferences.contains("${service.key}:certificate")

    fun extensionId(service: InstalledExtensionService): String? =
        preferences.getString("${service.key}:extension-id", null)

    fun isEnabled(service: InstalledExtensionService): Boolean =
        preferences.getBoolean("${service.key}:enabled", false)

    fun setEnabled(service: InstalledExtensionService, enabled: Boolean) {
        preferences.edit().putBoolean("${service.key}:enabled", enabled).apply()
    }

    fun grantedCapabilities(service: InstalledExtensionService): Set<String> =
        if (isTrusted(service)) capabilities(service.key) else emptySet()

    fun grantedCapabilities(extensionId: String): Set<String> {
        val serviceKeys = trustedServiceKeys(extensionId)
        if (serviceKeys.size != 1) return emptySet()
        return capabilities(serviceKeys.single())
    }

    fun isExtensionIdClaimedByAnotherService(
        service: InstalledExtensionService,
        extensionId: String,
    ): Boolean = trustedServiceKeys(extensionId).any { serviceKey -> serviceKey != service.key }

    fun isSoleTrustedOwner(
        service: InstalledExtensionService,
        extensionId: String,
    ): Boolean = isTrusted(service) &&
        extensionId(service) == extensionId &&
        isSoleStoredOwner(service.packageName, service.serviceName, extensionId)

    fun isSoleStoredOwner(
        packageName: String,
        serviceName: String,
        extensionId: String,
    ): Boolean = trustedServiceKeys(extensionId) == setOf(serviceKey(packageName, serviceName))

    fun trustedServices(): List<TrustedExtensionService> = preferences.all.keys
        .asSequence()
        .filter { key -> key.endsWith(CERTIFICATE_SUFFIX) }
        .mapNotNull { key ->
            val storedServiceKey = key.removeSuffix(CERTIFICATE_SUFFIX)
            val separator = storedServiceKey.indexOf(SERVICE_KEY_SEPARATOR)
            if (separator <= 0 || separator == storedServiceKey.lastIndex) return@mapNotNull null
            val certificate = preferences.getString(key, null) ?: return@mapNotNull null
            TrustedExtensionService(
                packageName = storedServiceKey.substring(0, separator),
                serviceName = storedServiceKey.substring(separator + 1),
                certificateSha256 = certificate,
                extensionId = preferences.getString("$storedServiceKey$EXTENSION_ID_SUFFIX", null),
                enabled = preferences.getBoolean("$storedServiceKey$ENABLED_SUFFIX", false),
                capabilities = capabilities(storedServiceKey),
                displayName = preferences.getString("$storedServiceKey$DISPLAY_NAME_SUFFIX", null),
                version = preferences.getString("$storedServiceKey$VERSION_SUFFIX", null),
                developer = preferences.getString("$storedServiceKey$DEVELOPER_SUFFIX", null),
            )
        }
        .sortedWith(
            compareBy(TrustedExtensionService::packageName, TrustedExtensionService::serviceName)
        )
        .toList()

    fun displayName(service: InstalledExtensionService): String? =
        preferences.getString("${service.key}:display-name", null)

    fun version(service: InstalledExtensionService): String? =
        preferences.getString("${service.key}:version", null)

    fun developer(service: InstalledExtensionService): String? =
        preferences.getString("${service.key}:developer", null)

    private fun trustedServiceKeys(extensionId: String): Set<String> = preferences.all.entries
        .asSequence()
        .filter { (key, value) -> key.endsWith(EXTENSION_ID_SUFFIX) && value == extensionId }
        .map { (key, _) -> key.removeSuffix(EXTENSION_ID_SUFFIX) }
        .filter { serviceKey -> preferences.contains("$serviceKey$CERTIFICATE_SUFFIX") }
        .toSet()

    private fun capabilities(serviceKey: String): Set<String> =
        preferences.getStringSet("$serviceKey$CAPABILITIES_SUFFIX", emptySet()).orEmpty().toSet()

    private val InstalledExtensionService.key: String
        get() = serviceKey(packageName, serviceName)

    private fun serviceKey(packageName: String, serviceName: String): String =
        "$packageName$SERVICE_KEY_SEPARATOR$serviceName"

    private companion object {
        const val SERVICE_KEY_SEPARATOR = "/"
        const val CERTIFICATE_SUFFIX = ":certificate"
        const val EXTENSION_ID_SUFFIX = ":extension-id"
        const val ENABLED_SUFFIX = ":enabled"
        const val CAPABILITIES_SUFFIX = ":capabilities"
        const val DISPLAY_NAME_SUFFIX = ":display-name"
        const val VERSION_SUFFIX = ":version"
        const val DEVELOPER_SUFFIX = ":developer"
    }
}

data class TrustedExtensionService(
    val packageName: String,
    val serviceName: String,
    val certificateSha256: String,
    val extensionId: String?,
    val enabled: Boolean,
    val capabilities: Set<String>,
    val displayName: String?,
    val version: String?,
    val developer: String?,
)
