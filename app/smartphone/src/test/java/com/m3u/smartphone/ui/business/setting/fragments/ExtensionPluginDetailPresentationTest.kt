package com.m3u.smartphone.ui.business.setting.fragments

import com.m3u.business.setting.ExtensionPluginDiscoveryState
import com.m3u.data.repository.plugin.InstalledPlugin
import com.m3u.extension.api.ExtensionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ExtensionPluginDetailPresentationTest {
    @Test
    fun `unresolved discovery never pretends that the plugin was removed`() {
        assertIs<ExtensionPluginDetailContentState.Loading>(
            resolve(ExtensionPluginDiscoveryState.Loading())
        )
        assertIs<ExtensionPluginDetailContentState.Failure>(
            resolve(ExtensionPluginDiscoveryState.Error())
        )
    }

    @Test
    fun `authoritative discovery is required before a plugin is missing`() {
        assertIs<ExtensionPluginDetailContentState.Missing>(
            resolve(ExtensionPluginDiscoveryState.Empty)
        )
        assertIs<ExtensionPluginDetailContentState.Missing>(
            resolve(
                ExtensionPluginDiscoveryState.Content(
                    listOf(plugin(serviceName = "another.Service"))
                )
            )
        )
    }

    @Test
    fun `refresh keeps the previous detail content and its discovery status`() {
        val target = plugin()
        val refreshing = assertIs<ExtensionPluginDetailContentState.Content>(
            resolve(ExtensionPluginDiscoveryState.Loading(listOf(target)))
        )
        val failed = assertIs<ExtensionPluginDetailContentState.Content>(
            resolve(ExtensionPluginDiscoveryState.Error(listOf(target)))
        )
        val ready = assertIs<ExtensionPluginDetailContentState.Content>(
            resolve(ExtensionPluginDiscoveryState.Content(listOf(target)))
        )

        assertSame(target, refreshing.plugin)
        assertEquals(
            ExtensionPluginDiscoveryStatus.REFRESHING,
            refreshing.discoveryStatus,
        )
        assertSame(target, failed.plugin)
        assertEquals(
            ExtensionPluginDiscoveryStatus.REFRESH_FAILED,
            failed.discoveryStatus,
        )
        assertSame(target, ready.plugin)
        assertEquals(ExtensionPluginDiscoveryStatus.READY, ready.discoveryStatus)
    }

    @Test
    fun `compact actions stack before localized labels become narrow columns`() {
        assertTrue(
            shouldStackExtensionPluginActions(
                availableWidthDp = 288f,
                fontScale = 1f,
                actionCount = 3,
            )
        )
        assertTrue(
            shouldStackExtensionPluginActions(
                availableWidthDp = 328f,
                fontScale = 1f,
                actionCount = 3,
            )
        )
        assertTrue(
            shouldStackExtensionPluginActions(
                availableWidthDp = 379f,
                fontScale = 1.3f,
                actionCount = 3,
            )
        )
        assertFalse(
            shouldStackExtensionPluginActions(
                availableWidthDp = 400f,
                fontScale = 1f,
                actionCount = 3,
            )
        )
        assertFalse(
            shouldStackExtensionPluginActions(
                availableWidthDp = 568f,
                fontScale = 1.3f,
                actionCount = 3,
            )
        )
        assertTrue(
            shouldStackExtensionPluginActions(
                availableWidthDp = 608f,
                fontScale = 2f,
                actionCount = 3,
            )
        )
        assertFalse(
            shouldStackExtensionPluginActions(
                availableWidthDp = 120f,
                fontScale = 2f,
                actionCount = 1,
            )
        )
    }

    private fun resolve(
        state: ExtensionPluginDiscoveryState,
    ): ExtensionPluginDetailContentState =
        resolveExtensionPluginDetailContentState(
            discoveryState = state,
            packageName = PACKAGE_NAME,
            serviceName = SERVICE_NAME,
        )

    private fun plugin(
        serviceName: String = SERVICE_NAME,
    ): InstalledPlugin = InstalledPlugin(
        packageName = PACKAGE_NAME,
        serviceName = serviceName,
        certificateSha256 = "AA",
        previousCertificateSha256 = null,
        trusted = false,
        signatureChanged = false,
        extensionId = null,
        enabled = false,
        state = ExtensionState.DISABLED,
        displayName = "Reference",
        version = "1.0.0",
        developer = "M3UAndroid",
        requestedCapabilities = emptySet(),
        grantedCapabilities = emptySet(),
        capabilityPermissions = emptyList(),
        inspectionError = null,
        installed = true,
        canClearData = false,
    )

    private companion object {
        const val PACKAGE_NAME = "com.example.reference"
        const val SERVICE_NAME = "com.example.reference.ExtensionService"
    }
}
