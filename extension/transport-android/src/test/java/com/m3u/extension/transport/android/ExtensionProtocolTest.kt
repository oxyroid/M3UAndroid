package com.m3u.extension.transport.android

import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersion
import com.m3u.extension.api.security.BrokerProtocolVersions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ExtensionProtocolTest {
    private val json = Json { explicitNulls = false }

    @Test
    fun `transport v2 handshake carries broker negotiation`() {
        val request = ExtensionHandshakeRequest(
            transportVersion = ExtensionProtocol.TRANSPORT_VERSION,
            hostApiVersion = ExtensionApiVersion(1, 0),
            supportedBrokerProtocolVersions = BrokerProtocolVersions.Supported,
        )
        assertEquals(
            """{"transportVersion":2,"hostApiVersion":{"major":1,"minor":0},"supportedBrokerProtocolVersions":[4]}""",
            json.encodeToString(request),
        )

        val response = ExtensionHandshakeResponse(
            transportVersion = ExtensionProtocol.TRANSPORT_VERSION,
            extensionApiRange = ExtensionApiRange(
                minimum = ExtensionApiVersion(1, 0),
                maximum = ExtensionApiVersion(1, 0),
            ),
            brokerProtocolVersion = BrokerProtocolVersions.Current,
        )
        assertEquals(
            """{"transportVersion":2,"extensionApiRange":{"minimum":{"major":1,"minor":0},"maximum":{"major":1,"minor":0}},"brokerProtocolVersion":4}""",
            json.encodeToString(response),
        )

        val rejected = ExtensionHandshakeResponse(
            transportVersion = ExtensionProtocol.TRANSPORT_VERSION,
            extensionApiRange = response.extensionApiRange,
            error = ExtensionHandshakeError(
                code = "broker.incompatible",
                message = "No shared broker protocol",
            ),
        )
        assertEquals(
            """{"transportVersion":2,"extensionApiRange":{"minimum":{"major":1,"minor":0},"maximum":{"major":1,"minor":0}},"error":{"code":"broker.incompatible","message":"No shared broker protocol"}}""",
            json.encodeToString(rejected),
        )
    }
}
