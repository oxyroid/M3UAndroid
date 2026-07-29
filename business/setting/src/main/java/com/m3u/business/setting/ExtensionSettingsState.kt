package com.m3u.business.setting

import com.m3u.data.repository.extension.ExtensionSettingsConfiguration
import com.m3u.extension.api.ExtensionId

sealed interface ExtensionSettingsState {
    val extensionId: ExtensionId?

    data object Closed : ExtensionSettingsState {
        override val extensionId: ExtensionId? = null
    }

    data class Loading(
        override val extensionId: ExtensionId,
    ) : ExtensionSettingsState

    data class Content(
        val configuration: ExtensionSettingsConfiguration,
        val updatingKeys: Set<String> = emptySet(),
    ) : ExtensionSettingsState {
        override val extensionId: ExtensionId = configuration.extensionId
    }

    data class Unavailable(
        override val extensionId: ExtensionId,
    ) : ExtensionSettingsState

    data class Error(
        override val extensionId: ExtensionId,
    ) : ExtensionSettingsState
}

internal fun ExtensionSettingsConfiguration?.toExtensionSettingsState(
    extensionId: ExtensionId,
    updatingKeys: Set<String> = emptySet(),
): ExtensionSettingsState =
    this?.let { configuration ->
        ExtensionSettingsState.Content(
            configuration = configuration,
            updatingKeys = updatingKeys,
        )
    }
        ?: ExtensionSettingsState.Unavailable(extensionId)
