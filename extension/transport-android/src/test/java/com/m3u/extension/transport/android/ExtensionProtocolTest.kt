package com.m3u.extension.transport.android

import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersion
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.Json

class ExtensionProtocolTest {
    private val json = Json { explicitNulls = false }

    @Test
    fun `malformed wire contracts are classified as incompatible`() {
        assertFailsWith<ExtensionTransportIncompatibleException> {
            decodeExtensionHandshake("""{"transportVersion":1""", json)
        }
        assertFailsWith<ExtensionTransportIncompatibleException> {
            decodeExtensionManifest("""{"id":"missing-required-fields"}""", json)
        }
    }

    @Test
    fun `deterministic handshake rejection is classified as incompatible`() {
        val response = ExtensionHandshakeResponse(
            transportVersion = ExtensionProtocol.TRANSPORT_VERSION,
            extensionApiRange = ExtensionApiRange(
                minimum = ExtensionApiVersion(1, 0),
                maximum = ExtensionApiVersion(1, 0),
            ),
            error = ExtensionHandshakeError(
                code = "broker.incompatible",
                message = "No shared broker protocol",
            ),
        )

        assertFailsWith<ExtensionTransportIncompatibleException> {
            requireCompatibleHandshake(response)
        }
    }
}
