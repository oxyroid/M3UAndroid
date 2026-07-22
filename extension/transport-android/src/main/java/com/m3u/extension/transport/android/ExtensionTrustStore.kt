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

    fun revoke(service: InstalledExtensionService) {
        preferences.edit()
            .remove("${service.key}:certificate")
            .remove("${service.key}:extension-id")
            .remove("${service.key}:enabled")
            .remove("${service.key}:capabilities")
            .remove("${service.key}:display-name")
            .remove("${service.key}:version")
            .remove("${service.key}:developer")
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

    fun grantedCapabilities(extensionId: String): Set<String> {
        val serviceKey = preferences.all.entries.firstOrNull { (key, value) ->
            key.endsWith(":extension-id") && value == extensionId
        }?.key?.removeSuffix(":extension-id") ?: return emptySet()
        return preferences.getStringSet("$serviceKey:capabilities", emptySet()).orEmpty().toSet()
    }

    fun displayName(service: InstalledExtensionService): String? =
        preferences.getString("${service.key}:display-name", null)

    fun version(service: InstalledExtensionService): String? =
        preferences.getString("${service.key}:version", null)

    fun developer(service: InstalledExtensionService): String? =
        preferences.getString("${service.key}:developer", null)

    private val InstalledExtensionService.key: String
        get() = "$packageName/$serviceName"
}
