package com.m3u.data.repository

import com.m3u.core.architecture.pref.annotation.PlaylistStrategy
import com.m3u.core.wrapper.Process
import com.m3u.core.wrapper.Resource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.PlaylistWithStreams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
interface PlaylistRepository : ReadOnlyRepository<Playlist, String> {
    override fun observe(url: String): Flow<Playlist?>
    fun observeWithStreams(url: String): Flow<PlaylistWithStreams?>
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
): Flow<Resource<Unit>> = channelFlow {
    try {
        val playlist = get(url) ?: error("Cannot find playlist: $url")
        if (playlist.local) {
            // refreshing is not needed for local storage playlist.
            send(Resource.Success(Unit))
            return@channelFlow
        }
        subscribe(playlist.title, url, strategy)
            .onEach { prev ->
                when (prev) {
                    is Process.Failure -> Resource.Failure(prev.message)
                    is Process.Loading -> Resource.Loading
                    is Process.Success -> Resource.Success(prev.data)
                }
                    .let { send(it) }
            }
            .launchIn(this)
    } catch (e: Exception) {
        send(Resource.Failure(e.message))
    }
}
