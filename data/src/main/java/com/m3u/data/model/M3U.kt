package com.m3u.data.model

import com.m3u.data.entity.Live

data class M3U(
    val id: String = "",
    val name: String = "",
    val logo: String = "",
    val group: String = "",
    val title: String = "",
    val url: String = ""
)


fun M3U.toLive(
    subscriptionId: Int
): Live = Live(
    url = url,
    label = title,
    cover = logo,
    subscriptionId = subscriptionId
)