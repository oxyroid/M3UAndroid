package com.m3u.extension.transport.android

import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersion
import kotlinx.serialization.Serializable

object ExtensionProtocol {
    const val SERVICE_ACTION = "com.m3u.extension.action.BIND_EXTENSION"
    const val HOST_BIND_PERMISSION = "com.m3u.permission.BIND_EXTENSION_HOST"
    const val METADATA_API_MAJOR = "com.m3u.extension.API_MAJOR"
    const val TRANSPORT_VERSION = 1
}

@Serializable
data class ExtensionHandshakeRequest(
    val transportVersion: Int,
    val hostApiVersion: ExtensionApiVersion,
)

@Serializable
data class ExtensionHandshakeResponse(
    val transportVersion: Int,
    val extensionApiRange: ExtensionApiRange,
)

data class InstalledExtensionService(
    val packageName: String,
    val serviceName: String,
    val certificateSha256: String,
    val uid: Int,
    val incompatibilityReason: String? = null,
)
