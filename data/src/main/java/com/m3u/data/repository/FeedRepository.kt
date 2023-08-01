package com.m3u.data.repository

import com.m3u.core.annotation.FeedStrategy
import com.m3u.core.wrapper.ProgressResource
import com.m3u.data.database.entity.Feed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

interface FeedRepository {
    fun observe(url: String): Flow<Feed?>
    fun observeAll(): Flow<List<Feed>>
    suspend fun get(url: String): Feed?
    fun subscribe(
        title: String,
        url: String,
        @FeedStrategy strategy: Int = FeedStrategy.ALL
    ): Flow<ProgressResource<Unit>>

    suspend fun unsubscribe(url: String): Feed?

    suspend fun rename(url: String, target: String)
}

fun FeedRepository.refresh(
    url: String,
    @FeedStrategy strategy: Int
): Flow<ProgressResource<Unit>> = channelFlow {
    try {
        val feed = get(url) ?: error("Cannot find feed: $url")
        subscribe(feed.title, url, strategy)
            .onEach(::send)
            .launchIn(this)
    } catch (e: Exception) {
        send(ProgressResource.Failure(e.message))
    }
}
