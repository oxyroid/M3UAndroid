package com.m3u.extension.transport.android

data class InstalledExtensionService(
    val packageName: String,
    val serviceName: String,
    val certificateSha256: String,
    val uid: Int,
    val incompatibilityReason: String? = null,
)
