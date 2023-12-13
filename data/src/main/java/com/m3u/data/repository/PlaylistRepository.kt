package com.m3u.data.repository

import com.m3u.core.annotation.PlaylistStrategy
import com.m3u.core.wrapper.Process
import com.m3u.data.database.entity.Playlist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
interface PlaylistRepository : ReadOnlyRepository<Playlist, String> {
    override fun observe(url: String): Flow<Playlist?>
    override suspend fun get(url: String): Playlist?

    fun subscribe(
        title: String,
        url: String,
        @PlaylistStrategy strategy: Int = PlaylistStrategy.ALL
    ): Flow<Process<Unit>>

    suspend fun unsubscribe(url: String): Playlist?

    suspend fun rename(url: String, target: String)
}

fun PlaylistRepository.refresh(
    url: String,
    @PlaylistStrategy strategy: Int
): Flow<Process<Unit>> = channelFlow {
    try {
        val playlist = get(url) ?: error("Cannot find playlist: $url")
        if (playlist.local) {
            // refreshing is not needed for local storage playlist.
            send(Process.Success(Unit))
            return@channelFlow
        }
        subscribe(playlist.title, url, strategy)
            .onEach(::send)
            .launchIn(this)
    } catch (e: Exception) {
        send(Process.Failure(e.message))
    }
}
