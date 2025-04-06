package com.m3u.extension.runtime

import com.m3u.data.database.dao.ChannelDao
import com.m3u.data.database.dao.PlaylistDao

interface RemoteServiceDependencies {
    val playlistDao: PlaylistDao
    val channelDao: ChannelDao
}