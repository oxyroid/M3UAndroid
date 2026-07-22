package com.m3u.extension.transport.android

object ExtensionProtocol {
    const val SERVICE_ACTION = "com.m3u.extension.action.BIND_EXTENSION"
    const val HOST_BIND_PERMISSION = "com.m3u.permission.BIND_EXTENSION_HOST"
    const val METADATA_API_MAJOR = "com.m3u.extension.API_MAJOR"
}

data class InstalledExtensionService(
    val packageName: String,
    val serviceName: String,
    val certificateSha256: String,
)
