package com.m3u.business.setting

import com.m3u.data.repository.plugin.InstalledPlugin
import com.m3u.extension.api.ExtensionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class ExtensionPluginDiscoveryStateTest {
    @Test
    fun emptyDiscoveryIsDistinctFromLoadingAndFailure() {
        val state = emptyList<InstalledPlugin>().toExtensionPluginDiscoveryState()

        assertSame(ExtensionPluginDiscoveryState.Empty, state)
        assertEquals(emptyList(), state.plugins)
    }

    @Test
    fun nonEmptyDiscoveryBecomesContent() {
        val plugin = plugin()

        val state = listOf(plugin).toExtensionPluginDiscoveryState()

        assertEquals(listOf(plugin), assertIs<ExtensionPluginDiscoveryState.Content>(state).plugins)
    }

    @Test
    fun loadingAndErrorCanRetainLastSuccessfulListWithoutBecomingContent() {
        val plugins = listOf(plugin())

        val loading = ExtensionPluginDiscoveryState.Loading(plugins)
        val error = ExtensionPluginDiscoveryState.Error(plugins)

        assertEquals(plugins, loading.plugins)
        assertEquals(plugins, error.plugins)
        assertIs<ExtensionPluginDiscoveryState.Loading>(loading)
        assertIs<ExtensionPluginDiscoveryState.Error>(error)
    }

    @Test
    fun contentRejectsAnEmptyList() {
        assertFailsWith<IllegalArgumentException> {
            ExtensionPluginDiscoveryState.Content(emptyList())
        }
    }

    private fun plugin() = InstalledPlugin(
        packageName = "dev.example.extension",
        serviceName = "dev.example.extension.Service",
        certificateSha256 = "AA",
        previousCertificateSha256 = null,
        trusted = false,
        signatureChanged = false,
        extensionId = "dev.example.extension",
        enabled = false,
        state = ExtensionState.DISABLED,
        displayName = "Example",
        version = "1.0.0",
        developer = "Example",
        requestedCapabilities = emptySet(),
        grantedCapabilities = emptySet(),
        capabilityPermissions = emptyList(),
        inspectionError = null,
        installed = true,
        canClearData = false,
    )
}
