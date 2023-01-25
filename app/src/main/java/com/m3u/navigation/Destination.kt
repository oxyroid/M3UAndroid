package com.m3u.navigation


sealed interface Destination {
    data class Subscription(
        val id: Int
    ) : Destination

    data class Live(
        val id: Int
    ) : Destination
}