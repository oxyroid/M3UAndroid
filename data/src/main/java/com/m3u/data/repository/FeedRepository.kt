package com.m3u.data.repository

import com.m3u.core.annotation.FeedStrategy
import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.resourceChannelFlow
import com.m3u.core.wrapper.sendMessage
import com.m3u.data.database.entity.Feed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

interface FeedRepository {
    fun observe(url: String): Flow<Feed?>
    fun observeAll(): Flow<List<Feed>>
    suspend fun get(url: String): Feed?
    suspend fun unsubscribe(url: String): Feed?
    fun subscribe(
        title: String,
        url: String,
        @FeedStrategy strategy: Int = FeedStrategy.ALL
    ): Flow<Resource<Unit>>
}

fun FeedRepository.refresh(
    url: String,
    @FeedStrategy strategy: Int
): Flow<Resource<Unit>> = resourceChannelFlow {
    val feed = get(url)
    if (feed == null) {
        sendMessage("Cannot find feed: $url")
        return@resourceChannelFlow
    }
    subscribe(feed.title, url, strategy)
        .onEach(::send)
        .launchIn(this)
}
