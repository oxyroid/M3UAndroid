@file:Suppress("unused")
package com.m3u.data.parser.xtream

import androidx.compose.runtime.Immutable

typealias XtreamChannelInfo = dev.oxyroid.parser.xtream.XtreamChannelInfo

@Immutable
data class XtreamEpisodeInfo(
    val containerExtension: String?,
    val episodeNum: String?,
    val id: String?,
    val title: String?
)

internal fun dev.oxyroid.parser.xtream.XtreamChannelInfo.Episode.toXtreamEpisodeInfo(): XtreamEpisodeInfo =
    XtreamEpisodeInfo(
        containerExtension = containerExtension,
        episodeNum = episodeNum,
        id = id,
        title = title
    )
