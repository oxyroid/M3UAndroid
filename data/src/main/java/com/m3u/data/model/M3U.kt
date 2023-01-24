package com.m3u.data.model

import com.m3u.core.util.Scheme
import com.m3u.core.util.Urls
import com.m3u.data.entity.Live
import com.m3u.data.entity.LiveState

data class M3U(
    val logo: String = "",
    val group: String = "",
    val title: String = "",
    val url: String = ""
)


fun M3U.toLive(): Live = Live(
    url = url,
    label = title,
    cover = logo,
    state = LiveState.Offline
)