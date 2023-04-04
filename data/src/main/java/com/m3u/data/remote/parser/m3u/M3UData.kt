package com.m3u.data.remote.parser.m3u

import com.m3u.data.database.entity.Live

data class M3UData(
    val id: String = "",
    val name: String = "",
    val cover: String = "",
    val group: String = "",
    val title: String = "",
    val url: String = "",
    val duration: Long = -1
)

fun M3UData.toLive(
    feedUrl: String
): Live = Live(
    url = url,
    group = group,
    title = title,
    cover = cover,
    feedUrl = feedUrl
)