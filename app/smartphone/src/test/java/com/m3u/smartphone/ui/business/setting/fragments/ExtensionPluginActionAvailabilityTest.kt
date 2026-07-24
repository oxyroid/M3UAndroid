package com.m3u.smartphone.ui.business.setting.fragments

import com.m3u.extension.api.ExtensionState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExtensionPluginActionAvailabilityTest {
    @Test
    fun `enabled unhealthy plugin keeps disable without exposing settings or enable`() {
        val actions = actions(
            enabled = true,
            state = ExtensionState.UNHEALTHY,
            hasInspectionError = true,
            trusted = true,
            hasAuthorizationToken = true,
        )

        assertTrue(actions.disable)
        assertFalse(actions.settings)
        assertFalse(actions.enable)
        assertTrue(actions.reauthorize)
    }

    @Test
    fun `enabled incompatible plugin keeps disable even when inspection failed`() {
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
    fun `healthy enabled plugin exposes settings and disable but never enable`() {
        val actions = actions(
            enabled = true,
            state = ExtensionState.ENABLED,
        )

        assertTrue(actions.settings)
        assertTrue(actions.disable)
        assertFalse(actions.enable)
    }

    @Test
    fun `only an eligible disabled plugin exposes enable`() {
        val eligible = actions(
            enabled = false,
            state = ExtensionState.DISABLED,
            hasAuthorizationToken = true,
        )
        val incompatible = actions(
            enabled = false,
            state = ExtensionState.INCOMPATIBLE,
            hasAuthorizationToken = true,
        )
        val inspectionFailure = actions(
            enabled = false,
            state = ExtensionState.DISABLED,
            hasInspectionError = true,
            hasAuthorizationToken = true,
        )

        assertTrue(eligible.enable)
        assertFalse(eligible.disable)
        assertFalse(incompatible.enable)
        assertFalse(inspectionFailure.enable)
    }

    @Test
    fun `signature change offers reauthorization independently of enable`() {
        val actions = actions(
            enabled = false,
            state = ExtensionState.DISABLED,
            signatureChanged = true,
            hasAuthorizationToken = true,
        )

        assertFalse(actions.enable)
        assertTrue(actions.reauthorize)
        assertTrue(actions.revoke)
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
