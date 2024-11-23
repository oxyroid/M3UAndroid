package com.m3u.data.runtime

import com.m3u.data.database.dao.ChannelDao
import com.m3u.data.database.dao.PlaylistDao
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.extension.api.model.EChannel
import com.m3u.extension.api.model.EPlaylist
import com.m3u.extension.api.tool.Saver
import javax.inject.Inject

internal class SaverImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val channelDao: ChannelDao,
) : Saver {
    override suspend fun savePlaylist(playlist: EPlaylist): Boolean = safe {
        playlistDao.insertOrReplace(playlist.toPlaylist())
    }

    override suspend fun saveChannel(channel: EChannel): Boolean = safe {
        channelDao.insertOrReplace(channel.toChannel())
    }

    private inline fun safe(block: () -> Unit): Boolean = kotlin.runCatching { block() }.isSuccess
}

private fun EPlaylist.toPlaylist(): Playlist = Playlist(
    title = title,
    url = url,
    source = DataSource.of(dataSource),
    userAgent = userAgent
)

private fun EChannel.toChannel(): Channel = Channel(
    url = url,
    category = category,
    title = title,
    cover = cover,
    playlistUrl = playlistUrl,
    licenseType = licenseType,
    licenseKey = licenseKey
)