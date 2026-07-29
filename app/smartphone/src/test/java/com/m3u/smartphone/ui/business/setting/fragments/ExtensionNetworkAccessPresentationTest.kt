package com.m3u.smartphone.ui.business.setting.fragments

import com.m3u.data.repository.extension.ExtensionNetworkOriginState
import com.m3u.data.repository.extension.ExtensionSettingNetworkOrigin
import com.m3u.data.repository.plugin.InstalledPlugin
import com.m3u.data.repository.plugin.PluginFixedNetworkOrigin
import com.m3u.data.repository.plugin.PluginNetworkAccess
import com.m3u.extension.api.ExtensionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExtensionNetworkAccessPresentationTest {
    @Test
    fun `detail counts fixed and configured setting origins without empty fields`() {
        val plugin = plugin(
            fixedStates = listOf(
                ExtensionNetworkOriginState.APPROVED,
                ExtensionNetworkOriginState.REQUIRES_APPROVAL,
            ),
            settingStates = ExtensionNetworkOriginState.entries,
        )

        assertEquals(
            ExtensionNetworkAccessCounts(approved = 2, total = 7),
            plugin.networkAccessCounts(),
        )
        assertEquals(
            ExtensionNetworkOriginState.entries
                .filterNot { it == ExtensionNetworkOriginState.NOT_CONFIGURED },
            plugin.visibleSettingNetworkOrigins().map { origin -> origin.state },
        )
    }

    @Test
    fun `only actionable setting origin states raise a detail warning`() {
        listOf(
            ExtensionNetworkOriginState.INVALID,
            ExtensionNetworkOriginState.REQUIRES_APPROVAL,
            ExtensionNetworkOriginState.SUSPENDED,
            ExtensionNetworkOriginState.UNVERIFIED,
        ).forEach { state ->
            assertTrue(plugin(settingStates = listOf(state)).hasSettingNetworkOriginWarning)
            assertTrue(state.requiresUserAttention)
        }

        listOf(
            ExtensionNetworkOriginState.NOT_CONFIGURED,
            ExtensionNetworkOriginState.APPROVED,
        ).forEach { state ->
            assertFalse(plugin(settingStates = listOf(state)).hasSettingNetworkOriginWarning)
            assertFalse(state.requiresUserAttention)
        }
    }

    private fun plugin(
        fixedStates: List<ExtensionNetworkOriginState> = emptyList(),
        settingStates: List<ExtensionNetworkOriginState> = emptyList(),
    ): InstalledPlugin = InstalledPlugin(
        packageName = "com.example.extension",
        serviceName = "com.example.extension.Service",
        certificateSha256 = "AA",
        previousCertificateSha256 = null,
        trusted = true,
        signatureChanged = false,
        extensionId = "com.example.extension",
        enabled = true,
        state = ExtensionState.ENABLED,
        displayName = "Example",
        version = "1.0.0",
        developer = "Example",
        requestedCapabilities = emptySet(),
        grantedCapabilities = emptySet(),
        capabilityPermissions = emptyList(),
        inspectionError = null,
        installed = true,
        canClearData = true,
        networkAccess = PluginNetworkAccess(
            fixedOrigins = fixedStates.mapIndexed { index, state ->
                PluginFixedNetworkOrigin(
                    origin = "https://fixed-$index.example:443",
                    state = state,
                )
            },
            settingOrigins = settingStates.mapIndexed { index, state ->
                ExtensionSettingNetworkOrigin(
                    sectionId = "network",
                    fieldKey = "origin-$index",
                    label = "Origin $index",
                    currentOrigin = when (state) {
                        ExtensionNetworkOriginState.NOT_CONFIGURED,
                        ExtensionNetworkOriginState.INVALID,
                        -> null

                        else -> "https://setting-$index.example:443"
                    },
                    state = state,
                )
            },
        ),
    )
}
