package com.m3u.data.parser.model

import android.net.Uri
import android.util.Log
import com.m3u.data.database.entity.Stream

data class M3UData(
    val id: String = "",
    val name: String = "",
    val cover: String = "",
    val group: String = "",
    val title: String = "",
    val url: String = "",
    val duration: Double = -1.0
)

fun M3UData.toStream(
    playlistUrl: String,
    seen: Long
): Stream {
    val fileScheme = "file:///"
    Log.e("TAG", "playlistUrl: $playlistUrl")
    val actualUrl = run {
        if (url.startsWith(fileScheme)) {
            Log.e("TAG", "url: $url")
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
    return Stream(
        url = actualUrl,
        group = group,
        title = title,
        cover = cover,
        playlistUrl = playlistUrl,
        seen = seen
    )

    // RESOLUTION
}
