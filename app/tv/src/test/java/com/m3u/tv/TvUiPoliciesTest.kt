package com.m3u.tv

import com.m3u.extension.api.ExtensionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvUiPoliciesTest {
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
