package com.m3u.data.extension

import com.m3u.core.extension.ExtensionChannelStore
import com.m3u.core.extension.ExtensionPlaylistStore
import com.m3u.data.database.dao.ChannelDao
import com.m3u.data.database.dao.PlaylistDao
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist
import javax.inject.Inject

internal class RoomExtensionPlaylistStore @Inject constructor(
    private val playlistDao: PlaylistDao
) : ExtensionPlaylistStore {
    override suspend fun insertOrReplace(
        title: String,
        url: String,
        userAgent: String?
    ): Long = playlistDao.insertOrReplace(
        Playlist(
            title = title,
            url = url,
            userAgent = userAgent
        )
    )
}

internal class RoomExtensionChannelStore @Inject constructor(
    private val channelDao: ChannelDao
) : ExtensionChannelStore {
    override suspend fun insertOrReplace(
        title: String,
        url: String,
        playlistUrl: String,
        cover: String?,
        category: String,
        licenseKey: String?,
        licenseType: String?
    ): Long = channelDao.insertOrReplace(
        Channel(
            title = title,
            url = url,
            playlistUrl = playlistUrl,
            cover = cover,
            category = category,
            licenseKey = licenseKey,
            licenseType = licenseType
        )
    )
}
