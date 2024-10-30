package com.m3u.extension.api.model

data class EPlaylist(
    val url: String,
    val title: String,
    val userAgent: String,
    val dataSource: String,
) : EMedia {
    companion object {
        // dataSource could be these or any other your custom values.
        const val DATA_SOURCE_M3U = "m3u"
        const val DATA_SOURCE_XTREAM = "xtream"
        const val DATA_SOURCE_EPG = "epg"
    }
}
