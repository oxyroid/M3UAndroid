package com.m3u.tv

import kotlin.math.roundToInt

internal enum class TvAppBackTarget {
    PLAYER,
    PROVIDER_SUBSCRIPTION,
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
): TvAppBackTarget = when {
    playerVisible -> TvAppBackTarget.PLAYER
    providerSubscriptionVisible -> TvAppBackTarget.PROVIDER_SUBSCRIPTION
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

internal fun tvProviderChoicePresentation(
    providerId: String,
    variantDisplayName: String,
): TvProviderChoicePresentation {
    val variantName = variantDisplayName.ifBlank { providerId }
    return TvProviderChoicePresentation(
        variantName = variantName,
        providerName = null,
    )
}

internal fun shouldRestoreTvStatusFocus(
    panelWasVisible: Boolean,
    panelIsVisible: Boolean,
    hasReturnTarget: Boolean,
): Boolean = panelWasVisible && !panelIsVisible && hasReturnTarget

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
