package com.m3u.data.codec

import kotlinx.serialization.Serializable

@Serializable
data class CodecPackManifest(
    val schemaVersion: Int,
    val packId: String,
    val loadOrder: List<String>,
    val artifacts: List<CodecPackArtifact>,
    val assets: Map<String, CodecPackAsset>
)

@Serializable
data class CodecPackArtifact(
    val group: String,
    val name: String,
    val version: String
)

@Serializable
data class CodecPackAsset(
    val fileName: String,
    val size: Long,
    val md5: String,
    val libraries: List<CodecPackLibrary>
)

@Serializable
data class CodecPackLibrary(
    val name: String,
    val size: Long,
    val md5: String
)

sealed interface CodecPackInstallResult {
    data object Disabled : CodecPackInstallResult
    data object Installed : CodecPackInstallResult
    data object AlreadyInstalled : CodecPackInstallResult
    data class UnsupportedAbi(val supportedAbis: List<String>) : CodecPackInstallResult
}