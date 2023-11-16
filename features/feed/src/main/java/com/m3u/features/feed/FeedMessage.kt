package com.m3u.features.feed

sealed class FeedMessage {
    data object FeedUrlNotFound : FeedMessage()
    data class FeedNotFound(val feedUrl: String) : FeedMessage()
    data object LiveNotFound : FeedMessage()
    data object LiveCoverNotFound : FeedMessage()
    data class LiveCoverSaved(val path: String) : FeedMessage()
}
