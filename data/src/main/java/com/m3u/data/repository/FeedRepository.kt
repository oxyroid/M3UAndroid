package com.m3u.data.repository

import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.resourceChanelFlow
import com.m3u.core.wrapper.sendMessage
import com.m3u.data.entity.Feed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.net.URL

interface FeedRepository {
    fun subscribe(title: String, url: URL): Flow<Resource<Unit>>
    fun observeAll(): Flow<List<Feed>>
    fun observe(url: String): Flow<Feed?>
    suspend fun get(url: String): Feed?
}

fun FeedRepository.sync(url: URL): Flow<Resource<Unit>> = resourceChanelFlow {
    val stringUrl = url.toString()
    val feed = get(stringUrl)
    if (feed == null) {
        sendMessage("Cannot find feed: $stringUrl")
        return@resourceChanelFlow
    }
    subscribe(feed.title, url)
        .onEach(::send)
        .launchIn(this)
}
