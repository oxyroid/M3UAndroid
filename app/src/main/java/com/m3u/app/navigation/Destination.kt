package com.m3u.app.navigation

sealed interface Destination {
    data class Feed(
        val url: String,
    ) : Destination

    data class Live(
        val id: Int,
    ) : Destination
}