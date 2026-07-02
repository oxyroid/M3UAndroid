package com.m3u.data.parser.m3u

import androidx.core.net.toUri
import com.m3u.data.database.model.Channel
import com.m3u.data.util.StreamUrlOptions

internal data class M3UData(
    val id: String = "",
    val name: String = "",
    val cover: String = "",
    val group: String = "",
    val title: String = "",
    val url: String = "",
    val videoUrl: String? = null,
    val duration: Double = -1.0,
    val licenseType: String? = null,
    val licenseKey: String? = null,
    val httpOptions: Map<String, String> = emptyMap(),
)

internal fun M3UData.toChannel(
    playlistUrl: String,
    seen: Long = 0L
): Channel {
    val absoluteUrl = url.toAbsoluteUrl(playlistUrl)
    val absoluteVideoUrl = videoUrl
        ?.takeIf { it.isNotBlank() }
        ?.toAbsoluteUrl(playlistUrl)

    /**
     * kodi adaptive: 'tvg-id' corresponds to 'channel-id' field in the EPG xml file.
     * If missing from the M3U file, the addon will use the 'tvg-name' tag to map the channel to the EPG.
     *
     * https://kodi.wiki/view/Add-on:PVR_IPTV_Simple_Client#Usage
     */
    val relationId = id.ifEmpty { name.ifEmpty { title } }

    return Channel(
        url = StreamUrlOptions.appendToUrl(
            absoluteUrl,
            httpOptions + buildMap {
                absoluteVideoUrl?.let { put(StreamUrlOptions.VIDEO_URL, it) }
            }
        ),
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

private fun String.toAbsoluteUrl(playlistUrl: String): String {
    val fileScheme = "file:///"
    if (!startsWith(fileScheme)) return this

    val relativePath = drop(fileScheme.length)
    return with(playlistUrl.toUri()) {
        val paths = pathSegments.dropLast(1) + relativePath
        buildUpon()
            .path(paths.joinToString("/", "", ""))
            .build()
            .toString()
    }
}
