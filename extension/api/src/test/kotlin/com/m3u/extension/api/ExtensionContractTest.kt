package com.m3u.extension.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExtensionContractTest {
    @Test
    fun `manifest accepts declared hook capabilities`() {
        val manifest = ExtensionManifest(
            id = ExtensionId("com.example.provider"),
            displayName = "Example",
            extensionVersion = "1.0.0",
            apiRange = ExtensionApiRange(
                minimum = ExtensionApiVersions.Current,
                maximum = ExtensionApiVersions.Current,
            ),
            hooks = setOf(
                ExtensionHookDeclaration(
                    hook = ExtensionHookIds.PlaybackSourceResolve,
                    requiredCapabilities = setOf(ExtensionCapabilityIds.PlaybackResolve),
                )
            ),
            capabilities = setOf(
                ExtensionCapabilityRequest(
                    capability = ExtensionCapabilityIds.PlaybackResolve,
                    reason = "Resolve a stable playback reference",
                )
            ),
        )

        assertEquals(ExtensionId("com.example.provider"), manifest.id)
        assertTrue(ExtensionApiVersions.Current in manifest.apiRange)
    }

    @Test
    fun `manifest rejects hook capability that was not requested`() {
        assertFailsWith<IllegalArgumentException> {
            ExtensionManifest(
                id = ExtensionId("com.example.provider"),
                displayName = "Example",
                extensionVersion = "1.0.0",
                apiRange = ExtensionApiRange(
                    minimum = ExtensionApiVersions.Current,
                    maximum = ExtensionApiVersions.Current,
                ),
                hooks = setOf(
                    ExtensionHookDeclaration(
                        hook = ExtensionHookIds.PlaybackSourceResolve,
                        requiredCapabilities = setOf(ExtensionCapabilityIds.PlaybackResolve),
                    )
                ),
                capabilities = emptySet(),
            )
        }
    }

    @Test
    fun `contract identifiers reject ambiguous values`() {
        assertFailsWith<IllegalArgumentException> { ExtensionId("Example Provider") }
        assertFailsWith<IllegalArgumentException> { Hook("playback/source") }
        assertFailsWith<IllegalArgumentException> { Capability("") }
    }
}
