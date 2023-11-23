package com.m3u.data.repository

import com.m3u.core.annotation.FeedStrategy
import com.m3u.core.wrapper.Process
import com.m3u.data.database.entity.Feed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
interface FeedRepository : ReadOnlyRepository<Feed, String> {
    override fun observe(url: String): Flow<Feed?>
    override suspend fun get(url: String): Feed?

    fun subscribe(
        title: String,
        url: String,
        @FeedStrategy strategy: Int = FeedStrategy.ALL
    ): Flow<Process<Unit>>

    suspend fun unsubscribe(url: String): Feed?

    suspend fun rename(url: String, target: String)
}

fun FeedRepository.refresh(
    url: String,
    @FeedStrategy strategy: Int
): Flow<Process<Unit>> = channelFlow {
    try {
        val feed = get(url) ?: error("Cannot find feed: $url")
        if (feed.local) {
            // refreshing is not needed for local storage feed.
            send(Process.Success(Unit))
            return@channelFlow
        }
        subscribe(feed.title, url, strategy)
            .onEach(::send)
            .launchIn(this)
    } catch (e: Exception) {
        send(Process.Failure(e.message))
    }
}
