package com.m3u.data.repository

import android.net.Uri
import com.m3u.core.architecture.pref.annotation.PlaylistStrategy
import com.m3u.core.wrapper.Resource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.PlaylistWithStreams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

interface PlaylistRepository {
    fun observeAll(): Flow<List<Playlist>>
    suspend fun get(url: String): Playlist?
    fun observe(url: String): Flow<Playlist?>
    fun observeWithStreams(url: String): Flow<PlaylistWithStreams?>
    suspend fun getWithStreams(url: String): PlaylistWithStreams?

    fun subscribe(
        title: String,
        url: String,
        @PlaylistStrategy strategy: Int = PlaylistStrategy.ALL
    ): Flow<Resource<Unit>>

    suspend fun unsubscribe(url: String): Playlist?

    suspend fun rename(url: String, target: String)

    suspend fun backupOrThrow(uri: Uri)

    suspend fun restoreOrThrow(uri: Uri)
}

fun PlaylistRepository.refresh(
    url: String,
    @PlaylistStrategy strategy: Int
): Flow<Resource<Unit>> = channelFlow {
    try {
        val playlist = checkNotNull(get(url)) { "Cannot find playlist: $url" }
        check(!playlist.fromLocal) { "refreshing is not needed for local storage playlist." }
        subscribe(playlist.title, url, strategy)
            .onEach(::send)
            .launchIn(this)
    } catch (e: Exception) {
        send(Resource.Failure(e.message))
    }
}
