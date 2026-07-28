package com.m3u.tv

import com.m3u.extension.api.ExtensionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TvUiPoliciesTest {
    @Test
    fun `default font scale preserves the compact tv layout`() {
        assertEquals(
            TvLargeTextLayout(
                heroMinHeightDp = 288,
                heroTextWidthFraction = 0.54f,
                playlistCardMinHeightDp = 144,
                metricTileMinHeightDp = 136,
                stackEmptyLibrary = false,
                emptySetupMinHeightDp = 356,
            ),
            tvLargeTextLayout(fontScale = 1f),
        )
    }

    @Test
    fun `two hundred percent text grows clipped surfaces and stacks empty state`() {
        val layout = tvLargeTextLayout(fontScale = 2f)
        assertEquals(528, layout.heroMinHeightDp)
        assertEquals(0.78f, layout.heroTextWidthFraction, absoluteTolerance = 0.0001f)
        assertEquals(216, layout.playlistCardMinHeightDp)
        assertEquals(208, layout.metricTileMinHeightDp)
        assertTrue(layout.stackEmptyLibrary)
        assertEquals(420, layout.emptySetupMinHeightDp)
    }

    @Test
    fun `large text policy clamps invalid and extreme scale inputs`() {
        assertEquals(tvLargeTextLayout(1f), tvLargeTextLayout(0.5f))
        assertEquals(tvLargeTextLayout(3f), tvLargeTextLayout(5f))
    }

    @Test
    fun `hero physical movement follows the visual order in ltr`() {
        assertEquals(
            TvHeroAction.SECONDARY,
            tvHeroActionAfterHorizontalMove(
                current = TvHeroAction.PRIMARY,
                direction = TvHorizontalDirection.RIGHT,
                isRtl = false,
            ),
        )
        assertEquals(
            TvHeroAction.PRIMARY,
            tvHeroActionAfterHorizontalMove(
                current = TvHeroAction.SECONDARY,
                direction = TvHorizontalDirection.LEFT,
                isRtl = false,
            ),
        )
        assertNull(
            tvHeroActionAfterHorizontalMove(
                current = TvHeroAction.PRIMARY,
                direction = TvHorizontalDirection.LEFT,
                isRtl = false,
            )
        )
        assertNull(
            tvHeroActionAfterHorizontalMove(
                current = TvHeroAction.SECONDARY,
                direction = TvHorizontalDirection.RIGHT,
                isRtl = false,
            )
        )
    }

    @Test
    fun `hero physical movement mirrors the visual order in rtl`() {
        assertEquals(
            TvHeroAction.SECONDARY,
            tvHeroActionAfterHorizontalMove(
                current = TvHeroAction.PRIMARY,
                direction = TvHorizontalDirection.LEFT,
                isRtl = true,
            ),
        )
        assertEquals(
            TvHeroAction.PRIMARY,
            tvHeroActionAfterHorizontalMove(
                current = TvHeroAction.SECONDARY,
                direction = TvHorizontalDirection.RIGHT,
                isRtl = true,
            ),
        )
        assertNull(
            tvHeroActionAfterHorizontalMove(
                current = TvHeroAction.SECONDARY,
                direction = TvHorizontalDirection.LEFT,
                isRtl = true,
            )
        )
        assertNull(
            tvHeroActionAfterHorizontalMove(
                current = TvHeroAction.PRIMARY,
                direction = TvHorizontalDirection.RIGHT,
                isRtl = true,
            )
        )
    }

    @Test
    fun `leading gradient mirrors both colors and midpoint in rtl`() {
        assertEquals(
            listOf(
                0f to "leading",
                0.58f to "middle",
                1f to "trailing",
            ),
            tvLeadingGradientColorStops(
                isRtl = false,
                leading = "leading",
                middle = "middle",
                trailing = "trailing",
                middlePosition = 0.58f,
            ),
        )
        assertEquals(
            listOf(
                0f to "trailing",
                (1f - 0.58f) to "middle",
                1f to "leading",
            ),
            tvLeadingGradientColorStops(
                isRtl = true,
                leading = "leading",
                middle = "middle",
                trailing = "trailing",
                middlePosition = 0.58f,
            ),
        )
    }

    @Test
    fun `ordinary browse delegates back to the activity`() {
        assertEquals(
            TvAppBackTarget.ACTIVITY,
            tvAppBackTarget(
                playerVisible = false,
                providerSubscriptionVisible = false,
                extensionSettingsVisible = false,
            ),
        )
    }

    @Test
    fun `provider form distinguishes loading from unavailable`() {
        assertEquals(
            TvProviderFormAvailability.LOADING,
            tvProviderFormAvailability(
                discoveryLoading = true,
                providerSupported = false,
                providerMarkedUnavailable = true,
            ),
        )
        assertEquals(
            TvProviderFormAvailability.UNAVAILABLE,
            tvProviderFormAvailability(
                discoveryLoading = false,
                providerSupported = false,
                providerMarkedUnavailable = false,
            ),
        )
        assertEquals(
            TvProviderFormAvailability.UNAVAILABLE,
            tvProviderFormAvailability(
                discoveryLoading = false,
                providerSupported = true,
                providerMarkedUnavailable = true,
            ),
        )
        assertEquals(
            TvProviderFormAvailability.AVAILABLE,
            tvProviderFormAvailability(
                discoveryLoading = false,
                providerSupported = true,
                providerMarkedUnavailable = false,
            ),
        )
    }

    @Test
    fun `provider submit requires an available idle provider`() {
        assertTrue(
            tvProviderSubmitEnabled(
                inProgress = false,
                availability = TvProviderFormAvailability.AVAILABLE,
            )
        )
        assertFalse(
            tvProviderSubmitEnabled(
                inProgress = true,
                availability = TvProviderFormAvailability.AVAILABLE,
            )
        )
        assertFalse(
            tvProviderSubmitEnabled(
                inProgress = false,
                availability = TvProviderFormAvailability.LOADING,
            )
        )
        assertFalse(
            tvProviderSubmitEnabled(
                inProgress = false,
                availability = TvProviderFormAvailability.UNAVAILABLE,
            )
        )
    }

    @Test
    fun `built in provider choice uses the selectable variant name`() {
        assertEquals(
            TvProviderChoicePresentation(
                variantName = "Jellyfin",
                providerName = null,
            ),
            tvProviderChoicePresentation(
                providerId = "builtin.media-server",
                providerDisplayName = "Emby / Jellyfin",
                variantDisplayName = "Jellyfin",
                external = false,
            ),
        )
    }

    @Test
    fun `external provider choice preserves a distinct plugin name`() {
        assertEquals(
            TvProviderChoicePresentation(
                variantName = "Jellyfin",
                providerName = "Living room provider",
            ),
            tvProviderChoicePresentation(
                providerId = "dev.example.provider",
                providerDisplayName = "Living room provider",
                variantDisplayName = "Jellyfin",
                external = true,
            ),
        )
        assertEquals(
            TvProviderChoicePresentation(
                variantName = "Jellyfin",
                providerName = null,
            ),
            tvProviderChoicePresentation(
                providerId = "dev.example.provider",
                providerDisplayName = "Jellyfin",
                variantDisplayName = "Jellyfin",
                external = true,
            ),
        )
    }

    @Test
    fun `status focus returns only after a transient panel closes`() {
        assertTrue(
            shouldRestoreTvStatusFocus(
                panelWasVisible = true,
                panelIsVisible = false,
                hasReturnTarget = true,
            )
        )
        assertFalse(
            shouldRestoreTvStatusFocus(
                panelWasVisible = false,
                panelIsVisible = false,
                hasReturnTarget = true,
            )
        )
        assertFalse(
            shouldRestoreTvStatusFocus(
                panelWasVisible = true,
                panelIsVisible = false,
                hasReturnTarget = false,
            )
        )
    }

    @Test
    fun `plugin trust mutations return to the stable developer mode control`() {
        listOf(
            TvExtensionPluginAction.DISABLE,
            TvExtensionPluginAction.ENABLE,
            TvExtensionPluginAction.REVOKE,
            TvExtensionPluginAction.REAUTHORIZE,
            TvExtensionPluginAction.CLEAR_DATA,
        ).forEach { action ->
            assertEquals(
                TvExtensionPluginReturnFocusAnchor.DEVELOPER_MODE,
                tvExtensionPluginReturnFocusAnchor(action),
            )
        }
        listOf(
            TvExtensionPluginAction.SETTINGS,
            TvExtensionPluginAction.EXPORT_DIAGNOSTICS,
        ).forEach { action ->
            assertEquals(
                TvExtensionPluginReturnFocusAnchor.SOURCE_ACTION,
                tvExtensionPluginReturnFocusAnchor(action),
            )
        }
    }

    @Test
    fun `developer mode item index accounts for dynamic provider rows`() {
        assertEquals(
            8,
            tvExtensionDeveloperModeItemIndex(
                providerFeedbackVisible = false,
                reauthenticationCount = 0,
                providerDiscoveryItemCount = 4,
                extensionErrorVisible = false,
            ),
        )
        assertEquals(
            12,
            tvExtensionDeveloperModeItemIndex(
                providerFeedbackVisible = true,
                reauthenticationCount = 2,
                providerDiscoveryItemCount = 4,
                extensionErrorVisible = true,
            ),
        )
    }

    @Test
    fun `provider return indexes account for feedback and reauthentication rows`() {
        assertEquals(
            5,
            tvProviderReauthenticationItemIndex(
                providerFeedbackVisible = true,
                reauthenticationIndex = 1,
            ),
        )
        assertEquals(
            8,
            tvProviderVariantItemIndex(
                providerFeedbackVisible = true,
                reauthenticationCount = 2,
                providerVariantIndex = 2,
            ),
        )
    }

    @Test
    fun `successful reauthentication returns to the stable provider variant`() {
        assertEquals(
            TvProviderReauthenticationFocusAnchor.ACCOUNT_ACTION,
            tvProviderReauthenticationFocusAnchor(
                subscriptionSucceeded = false,
                accountActionVisible = true,
            ),
        )
        listOf(
            true to true,
            false to false,
            true to false,
        ).forEach { (subscriptionSucceeded, accountActionVisible) ->
            assertEquals(
                TvProviderReauthenticationFocusAnchor.PROVIDER_VARIANT,
                tvProviderReauthenticationFocusAnchor(
                    subscriptionSucceeded = subscriptionSucceeded,
                    accountActionVisible = accountActionVisible,
                ),
            )
        }
    }

    @Test
    fun `cancelling a plugin panel returns to its source action`() {
        listOf(
            TvExtensionPluginAction.ENABLE,
            TvExtensionPluginAction.REVOKE,
            TvExtensionPluginAction.REAUTHORIZE,
            TvExtensionPluginAction.CLEAR_DATA,
        ).forEach { action ->
            assertEquals(
                TvExtensionPluginReturnFocusAnchor.SOURCE_ACTION,
                tvExtensionPluginReturnFocusAnchor(
                    action = action,
                    panelCancelled = true,
                ),
            )
        }
    }

    @Test
    fun `plugin source action availability is exposed for focus retry`() {
        val actions = TvExtensionPluginActionAvailability(
            settings = false,
            disable = true,
            enable = false,
            revoke = true,
            reauthorize = false,
            exportDiagnostics = true,
            clearData = false,
        )

        assertTrue(actions.isActionAvailable(TvExtensionPluginAction.DISABLE))
        assertTrue(actions.isActionAvailable(TvExtensionPluginAction.REVOKE))
        assertTrue(
            actions.isActionAvailable(TvExtensionPluginAction.EXPORT_DIAGNOSTICS)
        )
        assertFalse(actions.isActionAvailable(TvExtensionPluginAction.SETTINGS))
        assertFalse(actions.isActionAvailable(TvExtensionPluginAction.ENABLE))
        assertFalse(actions.isActionAvailable(TvExtensionPluginAction.REAUTHORIZE))
        assertFalse(actions.isActionAvailable(TvExtensionPluginAction.CLEAR_DATA))
    }

    @Test
    fun `app back handler preserves overlay priority`() {
        assertEquals(
            TvAppBackTarget.PLAYER,
            tvAppBackTarget(
                playerVisible = true,
                providerSubscriptionVisible = true,
                extensionSettingsVisible = true,
            ),
        )
        assertEquals(
            TvAppBackTarget.PROVIDER_SUBSCRIPTION,
            tvAppBackTarget(
                playerVisible = false,
                providerSubscriptionVisible = true,
                extensionSettingsVisible = true,
            ),
        )
        assertEquals(
            TvAppBackTarget.EXTENSION_SETTINGS,
            tvAppBackTarget(
                playerVisible = false,
                providerSubscriptionVisible = false,
                extensionSettingsVisible = true,
            ),
        )
    }

    @Test
    fun `enabled unhealthy plugin keeps disable without exposing settings or enable`() {
        val actions = actions(
            enabled = true,
            state = ExtensionState.UNHEALTHY,
            hasInspectionError = true,
        )

        assertTrue(actions.disable)
        assertFalse(actions.settings)
        assertFalse(actions.enable)
    }

    @Test
    fun `enabled incompatible plugin keeps disable after inspection failure`() {
        val actions = actions(
            enabled = true,
            state = ExtensionState.INCOMPATIBLE,
            hasInspectionError = true,
        )

        assertTrue(actions.disable)
        assertFalse(actions.settings)
        assertFalse(actions.enable)
    }

    @Test
    fun `only an eligible disabled plugin exposes enable`() {
        assertTrue(
            actions(
                enabled = false,
                state = ExtensionState.DISABLED,
                hasAuthorizationToken = true,
            ).enable
        )
        assertFalse(
            actions(
                enabled = false,
                state = ExtensionState.INCOMPATIBLE,
                hasAuthorizationToken = true,
            ).enable
        )
        assertFalse(
            actions(
                enabled = false,
                state = ExtensionState.DISABLED,
                hasInspectionError = true,
                hasAuthorizationToken = true,
            ).enable
        )
    }

    private fun actions(
        enabled: Boolean,
        state: ExtensionState,
        hasExtensionId: Boolean = true,
        installed: Boolean = true,
        signatureChanged: Boolean = false,
        hasInspectionError: Boolean = false,
        hasAuthorizationToken: Boolean = false,
        trusted: Boolean = false,
        canClearData: Boolean = false,
    ) = extensionPluginActionAvailability(
        enabled = enabled,
        state = state,
        hasExtensionId = hasExtensionId,
        installed = installed,
        signatureChanged = signatureChanged,
        hasInspectionError = hasInspectionError,
        hasAuthorizationToken = hasAuthorizationToken,
        trusted = trusted,
        canClearData = canClearData,
    )
}
