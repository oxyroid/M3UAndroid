package com.m3u.app.navigation

sealed interface Destination {
    object Root: Destination

    data class Feed(
        val url: String,
    ) : Destination

    data class Live(
        val id: Int,
    ) : Destination

    data class LivePlayList(
        val ids: List<Int>,
        val initialIndex: Int
    ) : Destination

    object Console : Destination
}