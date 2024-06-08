package com.m3u.data.parser.m3u

import android.net.Uri
import android.util.Log
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
    val actualUrl = run {
        if (url.startsWith(fileScheme)) {
            val paths = Uri.parse(playlistUrl)
                .pathSegments
                .dropLast(1) + url.drop(fileScheme.length)
            Uri.parse(playlistUrl)
                .buildUpon()
                .path(
                    paths.joinToString(
                        prefix = "",
                        postfix = "",
                        separator = "/"
                    )
                )
                .build()
                .toString()
        } else url
    }
    return Channel(
        url = actualUrl,
        category = group,
        title = title,
        cover = cover,
        playlistUrl = playlistUrl,
        seen = seen,
        licenseType = licenseType,
        licenseKey = licenseKey,
        originalId = id
    )
}