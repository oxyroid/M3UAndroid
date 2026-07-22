package com.m3u.extension.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class ExtensionContractTest {
    @Test
    fun `manifest accepts declared hook capabilities`() {
        val manifest = ExtensionManifest(
            id = ExtensionId("com.example.provider"),
            displayName = "Example",
            extensionVersion = ExtensionSemanticVersion(1, 0, 0),
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
                extensionVersion = ExtensionSemanticVersion(1, 0, 0),
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

    @Test
    fun `serialized envelope keeps old fixtures compatible and carries host grants`() {
        val json = Json { ignoreUnknownKeys = true }
        val oldFixture = """{"apiVersion":{"major":1,"minor":0},"invocationId":"call-1","extensionId":"com.example.provider","hook":"settings.schema.contribute","schemaVersion":1,"payload":{}}"""

        val decoded = json.decodeFromString<SerializedExtensionEnvelope>(oldFixture)

        assertTrue(decoded.grantedCapabilities.isEmpty())
        assertEquals(
            """{"apiVersion":{"major":1,"minor":0},"invocationId":"call-1","extensionId":"com.example.provider","hook":"settings.schema.contribute","schemaVersion":1,"payload":{},"grantedCapabilities":["settings.contribute"]}""",
            json.encodeToString(
                decoded.copy(
                    payload = JsonObject(emptyMap()),
                    grantedCapabilities = setOf(ExtensionCapabilityIds.SettingsContribute),
                )
            ),
        )
    }
}
