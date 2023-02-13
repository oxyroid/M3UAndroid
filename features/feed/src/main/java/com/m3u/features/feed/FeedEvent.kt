package com.m3u.features.feed

sealed interface FeedEvent {
    data class ObserveFeed(val url: String) : FeedEvent
    object FetchFeed : FeedEvent
    data class FavouriteLive(val id: Int, val target: Boolean) : FeedEvent
    data class MuteLive(val id: Int) : FeedEvent
    object ScrollUp : FeedEvent
}
