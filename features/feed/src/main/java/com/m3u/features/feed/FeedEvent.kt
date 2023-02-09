package com.m3u.features.feed

sealed interface FeedEvent {
    data class ObserveFeed(val url: String) : FeedEvent
    object FetchFeed : FeedEvent
    data class FavouriteLive(val id: Int) : FeedEvent
}
