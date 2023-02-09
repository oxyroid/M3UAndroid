package com.m3u.data.repository

import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.resourceChannelFlow
import com.m3u.core.wrapper.sendMessage
import com.m3u.data.entity.Feed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

interface FeedRepository {
    fun observe(url: String): Flow<Feed?>
    fun observeAll(): Flow<List<Feed>>
    suspend fun get(url: String): Feed?
    fun subscribe(title: String, url: String): Flow<Resource<Unit>>
}

fun FeedRepository.fetch(url: String): Flow<Resource<Unit>> = resourceChannelFlow {
    val feed = get(url)
    if (feed == null) {
        sendMessage("Cannot find feed: $url")
        return@resourceChannelFlow
    }
    subscribe(feed.title, url)
        .onEach(::send)
        .launchIn(this)
}
