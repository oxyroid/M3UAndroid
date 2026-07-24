package com.m3u.data.codec

import com.m3u.data.BuildConfig

object CodecPackConfig {
    const val DIRECTORY = "codec-packs"
    val enabled: Boolean = BuildConfig.NATIVE_PACK_ENABLED
    val packId: String = BuildConfig.NATIVE_PACK_ID
    val manifestPrefix: String = BuildConfig.NATIVE_PACK_MANIFEST_PREFIX
    val sourceRef: String = BuildConfig.NATIVE_PACK_REF
    val snapshotPath: String = BuildConfig.NATIVE_PACK_SNAPSHOT_PATH
    val expectedManifestSha256: String = BuildConfig.NATIVE_PACK_EXPECTED_MANIFEST_SHA256

    private val expectedAssetSha256ByFileName = parseDigests(
        BuildConfig.NATIVE_PACK_EXPECTED_ASSET_SHA256
    )
    private val expectedLibrarySha256ByPath = parseDigests(
        BuildConfig.NATIVE_PACK_EXPECTED_LIBRARY_SHA256
    )

    init {
        if (enabled) {
            require(expectedManifestSha256.isSha256()) {
                "The codec pack manifest identity is missing from this build."
            }
            require(expectedAssetSha256ByFileName.isNotEmpty()) {
                "The codec pack asset identities are missing from this build."
            }
            require(expectedLibrarySha256ByPath.isNotEmpty()) {
                "The codec library identities are missing from this build."
            }
        }
    }

    fun defaultManifestUrl(): String {
        check(enabled) { "Native pack is disabled for this build." }
        return assetUrl("$snapshotPath/$manifestPrefix-$packId.json")
    }

    fun assetUrl(path: String): String {
        check(enabled) { "Native pack is disabled for this build." }
        return "https://raw.githubusercontent.com/${BuildConfig.NATIVE_PACK_REPOSITORY}/$sourceRef/$path"
    }

    fun expectedAssetSha256(fileName: String): String? {
        return expectedAssetSha256ByFileName[fileName]
    }

    fun expectedLibrarySha256(assetFileName: String, libraryFileName: String): String? {
        return expectedLibrarySha256ByPath["$assetFileName/$libraryFileName"]
    }

    private fun parseDigests(encoded: String): Map<String, String> {
        if (encoded.isBlank()) return emptyMap()
        return encoded.split(';').associate { entry ->
            val separator = entry.indexOf('=')
            require(separator > 0 && separator < entry.lastIndex) {
                "Invalid codec pack identity entry."
            }
            val key = entry.substring(0, separator)
            val digest = entry.substring(separator + 1)
            require(digest.isSha256()) { "Invalid codec pack SHA-256 identity." }
            key to digest
        }
    }

    private fun String.isSha256(): Boolean {
        return length == 64 && all { character ->
            character in '0'..'9' || character in 'a'..'f' || character in 'A'..'F'
        }
    }
}
