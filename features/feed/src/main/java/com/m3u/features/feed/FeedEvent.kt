package com.m3u.features.feed

sealed interface FeedEvent {
    data class Observe(val url: String) : FeedEvent
    object Refresh : FeedEvent
    data class Favourite(val id: Int, val target: Boolean) : FeedEvent
    data class Mute(val id: Int, val target: Boolean) : FeedEvent
    data class SavePicture(val id: Int) : FeedEvent
    object ScrollUp : FeedEvent
    data class Query(val text: String) : FeedEvent
}
