package com.m3u.extension.runtime.business

import com.m3u.data.database.dao.ChannelDao
import com.m3u.data.database.dao.PlaylistDao
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist
import com.m3u.extension.api.Method
import com.m3u.extension.api.Module
import com.m3u.extension.api.business.SubscribeApi
import com.m3u.extension.api.model.AddChannelRequest
import com.m3u.extension.api.model.AddPlaylistRequest
import com.m3u.extension.api.model.Result
import com.m3u.extension.runtime.RemoteServiceDependencies
import kotlinx.coroutines.Dispatchers

@Module("subscribe")
class SubscribeModule(
    dependencies: RemoteServiceDependencies
) : RemoteModule(Dispatchers.IO), SubscribeApi {
    private val playlistDao: PlaylistDao = dependencies.playlistDao
    private val channelDao: ChannelDao = dependencies.channelDao

    @Method("addPlaylist")
    override suspend fun addPlaylist(req: AddPlaylistRequest): Result = result {
        playlistDao.insertOrReplace(
            Playlist(
                url = req.url,
                title = req.title,
                userAgent = req.user_agent
            )
        )
    }

    @Method("addChannel")
    override suspend fun addChannel(req: AddChannelRequest): Result = result {
        channelDao.insertOrReplace(
            Channel(
                title = req.title,
                url = req.url,
                playlistUrl = req.playlist_url,
                cover = req.cover,
                category = req.category.orEmpty(),
                licenseKey = req.license_key,
                licenseType = req.license_type
            )
        )
    }
}