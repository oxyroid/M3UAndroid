package com.m3u.data.repository.plugin

import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionState

interface ExtensionPluginRepository {
    suspend fun installedPlugins(): List<InstalledPlugin>
    suspend fun enable(packageName: String, serviceName: String): PluginEnableResult
    fun disable(extensionId: String): Boolean
    fun revoke(packageName: String, serviceName: String)
    suspend fun restoreEnabled(): Int
}

data class InstalledPlugin(
    val packageName: String,
    val serviceName: String,
    val certificateSha256: String,
    val trusted: Boolean,
    val signatureChanged: Boolean,
    val extensionId: String?,
    val enabled: Boolean,
    val state: ExtensionState,
    val displayName: String?,
    val version: String?,
    val developer: String?,
    val requestedCapabilities: Set<String>,
    val grantedCapabilities: Set<String>,
    val inspectionError: String?,
)

sealed interface PluginEnableResult {
    data class Enabled(val manifest: ExtensionManifest) : PluginEnableResult
    data class Rejected(val reason: String) : PluginEnableResult
}
