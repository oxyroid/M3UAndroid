package com.m3u.tv

import com.m3u.extension.api.ExtensionState
import kotlin.math.roundToInt

internal enum class TvAppBackTarget {
    PLAYER,
    PROVIDER_SUBSCRIPTION,
    EXTENSION_SETTINGS,
    ACTIVITY,
}

internal enum class TvHeroAction {
    PRIMARY,
    SECONDARY,
}

internal enum class TvHorizontalDirection {
    LEFT,
    RIGHT,
}

internal data class TvLargeTextLayout(
    val heroMinHeightDp: Int,
    val heroTextWidthFraction: Float,
    val playlistCardMinHeightDp: Int,
    val metricTileMinHeightDp: Int,
    val stackEmptyLibrary: Boolean,
    val emptySetupMinHeightDp: Int,
)

/**
 * Keeps the first TV viewport compact at the default scale while allowing
 * text containers to grow instead of clipping accessibility-sized text.
 */
internal fun tvLargeTextLayout(fontScale: Float): TvLargeTextLayout {
    val normalizedScale = fontScale.coerceIn(1f, 3f)
    val scaleDelta = normalizedScale - 1f
    return TvLargeTextLayout(
        heroMinHeightDp = (288f + 240f * scaleDelta).roundToInt(),
        heroTextWidthFraction = (0.54f + 0.24f * scaleDelta).coerceAtMost(0.82f),
        playlistCardMinHeightDp = (144f + 72f * scaleDelta).roundToInt(),
        metricTileMinHeightDp = (136f + 72f * scaleDelta).roundToInt(),
        stackEmptyLibrary = normalizedScale >= 1.35f,
        emptySetupMinHeightDp = (356f + 64f * scaleDelta).roundToInt(),
    )
}

/**
 * Resolves a physical DPad move against the action order that is actually
 * displayed by a layout-direction-aware Row.
 *
 * A null result means the focused hero is already at that physical edge and
 * must let Compose continue focus search outside the hero.
 */
internal fun tvHeroActionAfterHorizontalMove(
    current: TvHeroAction,
    direction: TvHorizontalDirection,
    isRtl: Boolean,
): TvHeroAction? {
    val actionAtLeft = if (isRtl) TvHeroAction.SECONDARY else TvHeroAction.PRIMARY
    val actionAtRight = if (isRtl) TvHeroAction.PRIMARY else TvHeroAction.SECONDARY
    val destination = when (direction) {
        TvHorizontalDirection.LEFT -> actionAtLeft
        TvHorizontalDirection.RIGHT -> actionAtRight
    }
    return destination.takeUnless { it == current }
}

/**
 * Maps a logical leading-to-trailing gradient onto physical color stops.
 */
internal fun <T> tvLeadingGradientColorStops(
    isRtl: Boolean,
    leading: T,
    middle: T,
    trailing: T,
    middlePosition: Float,
): List<Pair<Float, T>> = if (isRtl) {
    listOf(
        0f to trailing,
        (1f - middlePosition) to middle,
        1f to leading,
    )
} else {
    listOf(
        0f to leading,
        middlePosition to middle,
        1f to trailing,
    )
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

internal enum class TvProviderFormAvailability {
    AVAILABLE,
    LOADING,
    UNAVAILABLE,
}

internal fun tvProviderFormAvailability(
    discoveryLoading: Boolean,
    providerSupported: Boolean,
    providerMarkedUnavailable: Boolean,
): TvProviderFormAvailability = when {
    discoveryLoading -> TvProviderFormAvailability.LOADING
    providerMarkedUnavailable || !providerSupported -> TvProviderFormAvailability.UNAVAILABLE
    else -> TvProviderFormAvailability.AVAILABLE
}

internal fun tvProviderSubmitEnabled(
    inProgress: Boolean,
    availability: TvProviderFormAvailability,
): Boolean = !inProgress && availability == TvProviderFormAvailability.AVAILABLE

internal data class TvProviderChoicePresentation(
    val variantName: String,
    val providerName: String?,
)

/**
 * Built-in providers are presented as their selectable variant (for example,
 * Emby or Jellyfin). External providers additionally retain their plugin
 * identity when it differs from the variant name.
 */
internal fun tvProviderChoicePresentation(
    providerId: String,
    providerDisplayName: String,
    variantDisplayName: String,
    external: Boolean,
): TvProviderChoicePresentation {
    val variantName = variantDisplayName.ifBlank { providerId }
    val providerName = providerDisplayName.ifBlank { providerId }
    return TvProviderChoicePresentation(
        variantName = variantName,
        providerName = providerName.takeIf {
            external && providerName != variantName
        },
    )
}

internal fun shouldRestoreTvStatusFocus(
    panelWasVisible: Boolean,
    panelIsVisible: Boolean,
    hasReturnTarget: Boolean,
): Boolean = panelWasVisible && !panelIsVisible && hasReturnTarget

internal fun tvExtensionDeveloperModeItemIndex(
    providerFeedbackVisible: Boolean,
    reauthenticationCount: Int,
    providerDiscoveryItemCount: Int,
    extensionErrorVisible: Boolean,
): Int {
    require(reauthenticationCount >= 0)
    require(providerDiscoveryItemCount >= 0)
    return 3 +
        (if (providerFeedbackVisible) 1 else 0) +
        reauthenticationCount +
        providerDiscoveryItemCount +
        1 +
        (if (extensionErrorVisible) 1 else 0)
}

internal fun tvProviderReauthenticationItemIndex(
    providerFeedbackVisible: Boolean,
    reauthenticationIndex: Int,
): Int {
    require(reauthenticationIndex >= 0)
    return 3 +
        (if (providerFeedbackVisible) 1 else 0) +
        reauthenticationIndex
}

internal fun tvProviderVariantItemIndex(
    providerFeedbackVisible: Boolean,
    reauthenticationCount: Int,
    providerVariantIndex: Int,
): Int {
    require(reauthenticationCount >= 0)
    require(providerVariantIndex >= 0)
    return 3 +
        (if (providerFeedbackVisible) 1 else 0) +
        reauthenticationCount +
        providerVariantIndex
}

internal enum class TvProviderReauthenticationFocusAnchor {
    ACCOUNT_ACTION,
    PROVIDER_VARIANT,
}

internal fun tvProviderReauthenticationFocusAnchor(
    subscriptionSucceeded: Boolean,
    accountActionVisible: Boolean,
): TvProviderReauthenticationFocusAnchor =
    if (subscriptionSucceeded || !accountActionVisible) {
        TvProviderReauthenticationFocusAnchor.PROVIDER_VARIANT
    } else {
        TvProviderReauthenticationFocusAnchor.ACCOUNT_ACTION
    }

internal enum class TvExtensionPluginAction {
    SETTINGS,
    DISABLE,
    ENABLE,
    REVOKE,
    REAUTHORIZE,
    EXPORT_DIAGNOSTICS,
    CLEAR_DATA,
}

internal enum class TvExtensionPluginReturnFocusAnchor {
    SOURCE_ACTION,
    DEVELOPER_MODE,
}

/**
 * Executed mutations can remove their source action, so they return to the
 * stable developer-mode switch. Cancelling a panel keeps the source action
 * intact and returns there instead.
 */
internal fun tvExtensionPluginReturnFocusAnchor(
    action: TvExtensionPluginAction,
    panelCancelled: Boolean = false,
): TvExtensionPluginReturnFocusAnchor =
    if (panelCancelled) {
        TvExtensionPluginReturnFocusAnchor.SOURCE_ACTION
    } else {
        when (action) {
            TvExtensionPluginAction.DISABLE,
            TvExtensionPluginAction.ENABLE,
            TvExtensionPluginAction.REVOKE,
            TvExtensionPluginAction.REAUTHORIZE,
            TvExtensionPluginAction.CLEAR_DATA,
            -> TvExtensionPluginReturnFocusAnchor.DEVELOPER_MODE

            else -> TvExtensionPluginReturnFocusAnchor.SOURCE_ACTION
        }
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

internal fun TvExtensionPluginActionAvailability.isActionAvailable(
    action: TvExtensionPluginAction,
): Boolean = when (action) {
    TvExtensionPluginAction.SETTINGS -> settings
    TvExtensionPluginAction.DISABLE -> disable
    TvExtensionPluginAction.ENABLE -> enable
    TvExtensionPluginAction.REVOKE -> revoke
    TvExtensionPluginAction.REAUTHORIZE -> reauthorize
    TvExtensionPluginAction.EXPORT_DIAGNOSTICS -> exportDiagnostics
    TvExtensionPluginAction.CLEAR_DATA -> clearData
}

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
