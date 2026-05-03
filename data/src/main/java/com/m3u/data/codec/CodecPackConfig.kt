package com.m3u.data.codec

import com.m3u.data.BuildConfig

object CodecPackConfig {
    const val DIRECTORY = "codec-packs"
    val enabled: Boolean = BuildConfig.NATIVE_PACK_ENABLED
    val packId: String = BuildConfig.NATIVE_PACK_ID
    val manifestPrefix: String = BuildConfig.NATIVE_PACK_MANIFEST_PREFIX
    val sourceRef: String = BuildConfig.NATIVE_PACK_REF
    val snapshotPath: String = BuildConfig.NATIVE_PACK_SNAPSHOT_PATH

    fun defaultManifestUrl(): String {
        check(enabled) { "Native pack is disabled for this build." }
        return assetUrl("$snapshotPath/$manifestPrefix-$packId.json")
    }

    fun assetUrl(path: String): String {
        check(enabled) { "Native pack is disabled for this build." }
        return "https://raw.githubusercontent.com/${BuildConfig.NATIVE_PACK_REPOSITORY}/$sourceRef/$path"
    }
}
