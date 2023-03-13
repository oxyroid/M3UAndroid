package com.m3u.data.remote.analyzer.model

import com.m3u.data.local.entity.Live

data class M3U(
    val id: String = "",
    val name: String = "",
    val cover: String = "",
    val group: String = "",
    val title: String = "",
    val url: String = ""
)

fun M3U.toLive(
    feedUrl: String
): Live = Live(
    url = url,
    group = group,
    title = title,
    cover = cover,
    feedUrl = feedUrl
)