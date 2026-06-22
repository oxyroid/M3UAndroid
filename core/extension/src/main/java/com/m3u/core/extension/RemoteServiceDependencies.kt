package com.m3u.core.extension

interface RemoteServiceDependencies {
    val playlistStore: ExtensionPlaylistStore
    val channelStore: ExtensionChannelStore
}

interface ExtensionPlaylistStore {
    suspend fun insertOrReplace(
        title: String,
        url: String,
        userAgent: String?
    ): Long
}

interface ExtensionChannelStore {
    suspend fun insertOrReplace(
        title: String,
        url: String,
        playlistUrl: String,
        cover: String?,
        category: String,
        licenseKey: String?,
        licenseType: String?
    ): Long
}
