package com.m3u.features.feed

sealed interface FeedEvent {
    data class GetDetails(val url: String) : FeedEvent
    object Sync : FeedEvent
    data class AddToFavourite(val id: Int) : FeedEvent
}
