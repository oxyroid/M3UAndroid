package com.m3u.data.repository.plugin

import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionState

interface ExtensionPluginRepository {
    suspend fun installedPlugins(): List<InstalledPlugin>
    suspend fun enable(
        packageName: String,
        serviceName: String,
        authorizationToken: PluginAuthorizationToken,
    ): PluginEnableResult
    suspend fun reauthorize(
        packageName: String,
        serviceName: String,
        authorizationToken: PluginAuthorizationToken,
    ): PluginEnableResult
    suspend fun disable(extensionId: String): Boolean
    suspend fun revoke(packageName: String, serviceName: String)
    suspend fun clearData(packageName: String, serviceName: String): PluginDataClearResult
    suspend fun diagnostics(extensionId: String): String?
    suspend fun restoreEnabled(): Int
}

sealed interface PluginDataClearResult {
    data class Cleared(
        val clearedSettingValues: Int,
        val clearedCredentialHandles: Int,
        val clearedEpgSources: Int,
        val clearedMetadataOverlays: Int,
    ) : PluginDataClearResult

    data class Rejected(val reason: String) : PluginDataClearResult
}

data class InstalledPlugin(
    val packageName: String,
    val serviceName: String,
    val certificateSha256: String,
    val previousCertificateSha256: String?,
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
    val capabilityPermissions: List<PluginCapabilityPermission>,
    val inspectionError: String?,
    val installed: Boolean,
    val canClearData: Boolean,
    val networkOrigins: Set<String> = emptySet(),
    val approvedNetworkOrigins: Set<String> = emptySet(),
    val networkOriginSettingFields: Set<String> = emptySet(),
    val authorizationToken: PluginAuthorizationToken? = null,
)

class PluginAuthorizationToken internal constructor(
    internal val value: String,
) {
    override fun equals(other: Any?): Boolean =
        other is PluginAuthorizationToken && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "PluginAuthorizationToken(redacted)"
}

data class PluginCapabilityPermission(
    val id: String,
    val reason: String,
    val required: Boolean,
    val granted: Boolean,
)

sealed interface PluginEnableResult {
    data class Enabled(val manifest: ExtensionManifest) : PluginEnableResult
    data class Rejected(val reason: String) : PluginEnableResult
}
