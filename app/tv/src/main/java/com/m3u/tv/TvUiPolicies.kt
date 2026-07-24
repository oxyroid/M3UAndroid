package com.m3u.tv

import com.m3u.extension.api.ExtensionState

internal enum class TvAppBackTarget {
    PLAYER,
    PROVIDER_SUBSCRIPTION,
    EXTENSION_SETTINGS,
    ACTIVITY,
}

internal fun tvAppBackTarget(
    playerVisible: Boolean,
    providerSubscriptionVisible: Boolean,
    extensionSettingsVisible: Boolean,
): TvAppBackTarget = when {
    playerVisible -> TvAppBackTarget.PLAYER
    providerSubscriptionVisible -> TvAppBackTarget.PROVIDER_SUBSCRIPTION
    extensionSettingsVisible -> TvAppBackTarget.EXTENSION_SETTINGS
    else -> TvAppBackTarget.ACTIVITY
}

internal data class TvExtensionPluginActionAvailability(
    val settings: Boolean,
    val disable: Boolean,
    val enable: Boolean,
    val revoke: Boolean,
    val reauthorize: Boolean,
    val exportDiagnostics: Boolean,
    val clearData: Boolean,
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
) = TvExtensionPluginActionAvailability(
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
