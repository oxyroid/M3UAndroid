package com.m3u.data.parser.model

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
): Stream = Stream(
    url = url,
    group = group,
    title = title,
    cover = cover,
    playlistUrl = playlistUrl,
    seen = seen
)
