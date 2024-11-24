package com.m3u.data.runtime

import android.util.Log
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
    override suspend fun savePlaylist(pkgName: String, playlist: EPlaylist): Boolean = safe {
        playlist.toPlaylist(pkgName)?.let { playlistDao.insertOrReplace(it) }
    }

    override suspend fun saveChannel(channel: EChannel): Boolean = safe {
        channelDao.insertOrReplace(channel.toChannel())
    }

    private inline fun safe(block: () -> Unit): Boolean = runCatching {
        block()
    }
        .onFailure { Log.e("SaverImpl", "throw an error", it) }
        .isSuccess
}

private fun EPlaylist.toPlaylist(pkgName: String): Playlist? {
    return Playlist(
        title = title,
        url = url,
        source = DataSource.Ext(
            label = workflow.name,
            pkgName = pkgName,
            classPath = workflow::class.qualifiedName ?: return null
        ),
        userAgent = userAgent
    )
}

private fun EChannel.toChannel(): Channel = Channel(
    url = url,
    category = category,
    title = title,
    cover = cover,
    playlistUrl = playlistUrl,
    licenseType = licenseType,
    licenseKey = licenseKey
)