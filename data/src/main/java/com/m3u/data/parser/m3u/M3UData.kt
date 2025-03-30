package com.m3u.data.parser.m3u

import androidx.core.net.toUri
import com.m3u.data.database.model.Channel

internal data class M3UData(
    val id: String = "",
    val name: String = "",
    val cover: String = "",
    val group: String = "",
    val title: String = "",
    val url: String = "",
    val duration: Double = -1.0,
    val licenseType: String? = null,
    val licenseKey: String? = null,
)

internal fun M3UData.toChannel(
    playlistUrl: String,
    seen: Long = 0L
): Channel {
    val fileScheme = "file:///"
    val absoluteUrl = if (!url.startsWith(fileScheme)) url
    else {
        with(playlistUrl.toUri()) {
            val paths = pathSegments.dropLast(1) + url.drop(fileScheme.length)
            buildUpon()
                .path(
                    paths.joinToString("/", "", "")
                )
                .build()
                .toString()
        }
    }

    /**
     * kodi adaptive: 'tvg-id' corresponds to 'channel-id' field in the EPG xml file.
     * If missing from the M3U file, the addon will use the 'tvg-name' tag to map the channel to the EPG.
     *
     * https://kodi.wiki/view/Add-on:PVR_IPTV_Simple_Client#Usage
     */
    val relationId = id.ifEmpty { name }

    return Channel(
        url = absoluteUrl,
        category = group,
        title = title,
        cover = cover,
        playlistUrl = playlistUrl,
        seen = seen,
        licenseType = licenseType,
        licenseKey = licenseKey,
        relationId = relationId
    )
}