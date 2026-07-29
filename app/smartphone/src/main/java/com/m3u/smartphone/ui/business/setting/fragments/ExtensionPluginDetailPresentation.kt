package com.m3u.smartphone.ui.business.setting.fragments

import com.m3u.business.setting.ExtensionPluginDiscoveryState
import com.m3u.data.repository.plugin.InstalledPlugin

internal sealed interface ExtensionPluginDetailContentState {
    data object Loading : ExtensionPluginDetailContentState

    data object Missing : ExtensionPluginDetailContentState

    data object Failure : ExtensionPluginDetailContentState

    data class Content(
        val plugin: InstalledPlugin,
        val discoveryStatus: ExtensionPluginDiscoveryStatus,
    ) : ExtensionPluginDetailContentState
}

internal enum class ExtensionPluginDiscoveryStatus {
    READY,
    REFRESHING,
    REFRESH_FAILED,
}

internal fun resolveExtensionPluginDetailContentState(
    discoveryState: ExtensionPluginDiscoveryState,
    packageName: String,
    serviceName: String,
): ExtensionPluginDetailContentState {
    val plugin = discoveryState.plugins.singleOrNull { candidate ->
        candidate.packageName == packageName &&
            candidate.serviceName == serviceName
    }
    return when (discoveryState) {
        is ExtensionPluginDiscoveryState.Loading -> plugin?.let {
            ExtensionPluginDetailContentState.Content(
                plugin = it,
                discoveryStatus = ExtensionPluginDiscoveryStatus.REFRESHING,
            )
        } ?: ExtensionPluginDetailContentState.Loading

        is ExtensionPluginDiscoveryState.Error -> plugin?.let {
            ExtensionPluginDetailContentState.Content(
                plugin = it,
                discoveryStatus = ExtensionPluginDiscoveryStatus.REFRESH_FAILED,
            )
        } ?: ExtensionPluginDetailContentState.Failure

        is ExtensionPluginDiscoveryState.Content -> plugin?.let {
            ExtensionPluginDetailContentState.Content(
                plugin = it,
                discoveryStatus = ExtensionPluginDiscoveryStatus.READY,
            )
        } ?: ExtensionPluginDetailContentState.Missing

        ExtensionPluginDiscoveryState.Empty ->
            ExtensionPluginDetailContentState.Missing
    }
}
