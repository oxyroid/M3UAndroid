package com.m3u.extension.api

import com.m3u.extension.api.security.HostNetworkBrokerHooks
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExtensionNetworkOriginContractTest {
    @Test
    fun `fixed origins canonicalize scheme host and default port`() {
        assertEquals(
            "https://api.example.test:443",
            ExtensionNetworkOrigin("HTTPS://API.EXAMPLE.TEST").canonicalValue,
        )
        assertEquals(
            "http://127.0.0.1:8080",
            ExtensionNetworkOrigin("http://127.0.0.1:8080").canonicalValue,
        )
    }

    @Test
    fun `fixed origins reject wildcard path user info and invalid ports`() {
        listOf(
            "https://*.example.test",
            "https://api.example.test/",
            "https://user@api.example.test",
            "https://api.example.test/path",
            "https://api.example.test?query=true",
            "https://api.example.test#fragment",
            "ftp://api.example.test",
            "https://api.example.test:0",
            "https://api.example.test:65536",
            "https://[2001:db8::1]",
            "https://[:::]",
            "https://[2001:db8:::1]",
            "https://",
        ).forEach { origin ->
            assertFailsWith<IllegalArgumentException>(origin) {
                ExtensionNetworkOrigin(origin)
            }
        }
    }

    @Test
    fun `origin source fields require explicit non-secret text`() {
        assertTrue(
            ExtensionSettingField(
                key = "server",
                label = "Server",
                type = ExtensionSettingType.TEXT,
                networkOrigin = true,
            ).networkOrigin
        )
        assertFailsWith<IllegalArgumentException> {
            ExtensionSettingField(
                key = "token",
                label = "Token",
                type = ExtensionSettingType.SECRET,
                networkOrigin = true,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ExtensionSettingField(
                key = "server",
                label = "Server",
                type = ExtensionSettingType.TEXT,
                defaultValue = JsonPrimitive("https://api.example.test"),
                networkOrigin = true,
            )
        }
    }

    @Test
    fun `manifest origins require network capability and reject canonical duplicates`() {
        assertFailsWith<IllegalArgumentException> {
            manifest(
                origins = setOf(ExtensionNetworkOrigin("https://api.example.test")),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            manifest(
                capabilities = networkCapabilities(),
                origins = setOf(
                    ExtensionNetworkOrigin("https://api.example.test"),
                    ExtensionNetworkOrigin("https://API.EXAMPLE.TEST:443"),
                ),
            )
        }
        assertEquals(
            setOf("https://api.example.test:443"),
            manifest(
                capabilities = networkCapabilities(),
                origins = setOf(ExtensionNetworkOrigin("https://api.example.test")),
            ).networkOrigins.mapTo(mutableSetOf(), ExtensionNetworkOrigin::canonicalValue),
        )
    }

    @Test
    fun `network background task requires network on manifest and hook`() {
        val task = ExtensionBackgroundTaskDeclaration(
            taskId = "catalog.refresh",
            repeatIntervalHours = 24,
            requiresNetwork = true,
        )
        assertFailsWith<IllegalArgumentException> {
            backgroundManifest(task, hookNetwork = false, requestNetwork = false)
        }
        assertFailsWith<IllegalArgumentException> {
            backgroundManifest(task, hookNetwork = true, requestNetwork = false)
        }
        assertTrue(
            backgroundManifest(task, hookNetwork = true, requestNetwork = true)
                .backgroundTasks
                .single()
                .requiresNetwork
        )
    }

    @Test
    fun `generic production hooks support broker scopes while discovery remains offline`() {
        listOf(
            ExtensionHookIds.SubscriptionProviderValidate,
            ExtensionHookIds.SubscriptionContentRefresh,
            ExtensionHookIds.PlaybackSourceResolve,
            ExtensionHookIds.PlaybackSessionClose,
            ExtensionHookIds.MetadataChannelEnrich,
            ExtensionHookIds.EpgContentRefresh,
            ExtensionHookIds.SettingsSchemaContribute,
            ExtensionHookIds.SearchProviderQuery,
            ExtensionHookIds.BackgroundTaskRun,
        ).forEach { hook ->
            assertTrue(HostNetworkBrokerHooks.supports(hook), hook.id)
        }
        assertFalse(
            HostNetworkBrokerHooks.supports(ExtensionHookIds.SubscriptionProviderDiscover)
        )
    }

    private fun manifest(
        capabilities: Set<ExtensionCapabilityRequest> = emptySet(),
        origins: Set<ExtensionNetworkOrigin> = emptySet(),
    ) = ExtensionManifest(
        id = ExtensionId("com.example.network"),
        displayName = "Network example",
        extensionVersion = ExtensionSemanticVersion(1, 0, 0),
        apiRange = ExtensionApiRange(
            minimum = ExtensionApiVersions.Current,
            maximum = ExtensionApiVersions.Current,
        ),
        hooks = emptySet(),
        capabilities = capabilities,
        networkOrigins = origins,
    )

    private fun networkCapabilities() = setOf(
        ExtensionCapabilityRequest(
            capability = ExtensionCapabilityIds.Network,
            reason = "Connect to the declared service",
        )
    )

    private fun backgroundManifest(
        task: ExtensionBackgroundTaskDeclaration,
        hookNetwork: Boolean,
        requestNetwork: Boolean,
    ): ExtensionManifest {
        val hookCapabilities = buildSet {
            add(ExtensionCapabilityIds.BackgroundTask)
            if (hookNetwork) add(ExtensionCapabilityIds.Network)
        }
        val manifestCapabilities = buildSet {
            add(
                ExtensionCapabilityRequest(
                    ExtensionCapabilityIds.BackgroundTask,
                    "Run scheduled maintenance",
                )
            )
            if (requestNetwork) {
                add(
                    ExtensionCapabilityRequest(
                        ExtensionCapabilityIds.Network,
                        "Refresh remote data",
                    )
                )
            }
        }
        return ExtensionManifest(
            id = ExtensionId("com.example.background.network"),
            displayName = "Background network example",
            extensionVersion = ExtensionSemanticVersion(1, 0, 0),
            apiRange = ExtensionApiRange(
                minimum = ExtensionApiVersions.Current,
                maximum = ExtensionApiVersions.Current,
            ),
            hooks = setOf(
                ExtensionHookDeclaration(
                    hook = ExtensionHookIds.BackgroundTaskRun,
                    schemaVersion = HostHookSpecs.BackgroundTask.schemaVersion,
                    requiredCapabilities = hookCapabilities,
                )
            ),
            capabilities = manifestCapabilities,
            backgroundTasks = listOf(task),
        )
    }
}
