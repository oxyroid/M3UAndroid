package com.m3u.extension.transport.android

import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersion
import kotlinx.serialization.Serializable

object ExtensionProtocol {
    const val SERVICE_ACTION = "com.m3u.extension.action.BIND_EXTENSION"
    const val HOST_BIND_PERMISSION = "com.m3u.permission.BIND_EXTENSION_HOST"
    const val METADATA_API_MAJOR = "com.m3u.extension.API_MAJOR"
    const val TRANSPORT_VERSION = 2
}

@Serializable
data class ExtensionHandshakeRequest(
    val transportVersion: Int,
    val hostApiVersion: ExtensionApiVersion,
    val supportedBrokerProtocolVersions: Set<Int>,
)

@Serializable
data class ExtensionHandshakeResponse(
    val transportVersion: Int,
    val extensionApiRange: ExtensionApiRange,
    val brokerProtocolVersion: Int? = null,
    val error: ExtensionHandshakeError? = null,
) {
    init {
        require((brokerProtocolVersion != null) != (error != null)) {
            "Handshake response must contain either a broker version or an error"
        }
    }
}

@Serializable
data class ExtensionHandshakeError(
    val code: String,
    val message: String,
) {
    init {
        require(code.matches(Regex("[a-z0-9]+(?:[._-][a-z0-9]+)*"))) {
            "Invalid handshake error code"
        }
        require(message.isNotBlank()) { "Handshake error message must not be blank" }
    }
}

data class InstalledExtensionService(
    val packageName: String,
    val serviceName: String,
    val certificateSha256: String,
    val uid: Int,
    val incompatibilityReason: String? = null,
)
