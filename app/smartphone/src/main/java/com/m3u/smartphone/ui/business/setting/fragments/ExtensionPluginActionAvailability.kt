package com.m3u.smartphone.ui.business.setting.fragments

import com.m3u.data.repository.plugin.InstalledPlugin
import com.m3u.extension.api.ExtensionState

internal data class ExtensionPluginActionAvailability(
    val settings: Boolean,
    val disable: Boolean,
    val enable: Boolean,
    val revoke: Boolean,
    val reauthorize: Boolean,
    val exportDiagnostics: Boolean,
    val clearData: Boolean,
)

internal fun InstalledPlugin.actionAvailability() = extensionPluginActionAvailability(
    enabled = enabled,
    state = state,
    hasExtensionId = extensionId != null,
    installed = installed,
    signatureChanged = signatureChanged,
    hasInspectionError = inspectionError != null,
    hasAuthorizationToken = authorizationToken != null,
    trusted = trusted,
    canClearData = canClearData,
)

internal fun extensionPluginActionAvailability(
    enabled: Boolean,
    state: ExtensionState,
    hasExtensionId: Boolean,
    installed: Boolean,
    signatureChanged: Boolean,
    hasInspectionError: Boolean,
    hasAuthorizationToken: Boolean,
    trusted: Boolean,
    canClearData: Boolean,
) = ExtensionPluginActionAvailability(
    settings = enabled &&
        state == ExtensionState.ENABLED &&
        hasExtensionId,
    disable = enabled && hasExtensionId,
    enable = !enabled &&
        state == ExtensionState.DISABLED &&
        installed &&
        !signatureChanged &&
        !hasInspectionError &&
        hasAuthorizationToken,
    revoke = trusted || signatureChanged,
    reauthorize = installed &&
        (trusted || signatureChanged) &&
        hasAuthorizationToken,
    exportDiagnostics = installed && hasExtensionId,
    clearData = canClearData,
)
