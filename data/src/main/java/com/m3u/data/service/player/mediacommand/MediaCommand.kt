package com.m3u.data.service.player.mediacommand

import androidx.compose.runtime.Immutable
import com.m3u.data.parser.xtream.XtreamChannelInfo

@Immutable
sealed class MediaCommand(open val channelId: Int) {
    data class Common(override val channelId: Int) : MediaCommand(channelId)
    data class XtreamEpisode(
        override val channelId: Int,
        val episode: XtreamChannelInfo.Episode
    ) : MediaCommand(channelId)
}
