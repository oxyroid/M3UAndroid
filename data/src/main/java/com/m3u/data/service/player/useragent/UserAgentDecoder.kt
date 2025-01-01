package com.m3u.data.service.player.useragent

import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist

internal interface UserAgentDecoder {
    fun decodeUserAgent(
        channel: Channel,
        playlist: Playlist?
    ): String?
}