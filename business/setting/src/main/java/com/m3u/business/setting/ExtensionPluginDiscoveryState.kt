package com.m3u.business.setting

import com.m3u.data.repository.plugin.InstalledPlugin

sealed interface ExtensionPluginDiscoveryState {
    val plugins: List<InstalledPlugin>

    data class Loading(
        override val plugins: List<InstalledPlugin> = emptyList(),
    ) : ExtensionPluginDiscoveryState

    data object Empty : ExtensionPluginDiscoveryState {
        override val plugins: List<InstalledPlugin> = emptyList()
    }

    data class Content(
        override val plugins: List<InstalledPlugin>,
    ) : ExtensionPluginDiscoveryState {
        init {
            require(plugins.isNotEmpty()) {
                "Content requires at least one installed plugin"
            }
        }
    }

    data class Error(
        override val plugins: List<InstalledPlugin> = emptyList(),
    ) : ExtensionPluginDiscoveryState
}

internal fun List<InstalledPlugin>.toExtensionPluginDiscoveryState():
    ExtensionPluginDiscoveryState =
    if (isEmpty()) {
        ExtensionPluginDiscoveryState.Empty
    } else {
        ExtensionPluginDiscoveryState.Content(toList())
    }
