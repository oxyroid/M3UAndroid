package com.m3u.data.repository

import android.net.Uri
import com.m3u.core.architecture.pref.annotation.PlaylistStrategy
import com.m3u.core.wrapper.Resource
import com.m3u.data.database.model.DataSource
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

    fun m3u(
        title: String,
        url: String,
        @PlaylistStrategy strategy: Int = PlaylistStrategy.ALL
    ): Flow<Resource<Unit>>

    suspend fun xtream(
        title: String,
        address: String,
        username: String,
        password: String,
        @PlaylistStrategy strategy: Int = PlaylistStrategy.ALL
    )

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
        when (playlist.source) {
            DataSource.M3U -> {
                m3u(
                    title = playlist.title,
                    url = url,
                    strategy = strategy
                )
                    .onEach(::send)
                    .launchIn(this)
            }

            DataSource.Xtream -> {
                val regex = """(.+?)/player_api.php\?username=(.+)&password=(.+)""".toRegex()
                val matchEntire = checkNotNull(regex.matchEntire(playlist.url)) { "invalidate url" }
                send(Resource.Loading)
                xtream(
                    title = playlist.title,
                    address = matchEntire.groups[1]!!.value,
                    username = matchEntire.groups[2]!!.value,
                    password = matchEntire.groups[3]!!.value,
                )
                send(Resource.Success(Unit))
            }

            else -> throw RuntimeException("Refresh data source ${playlist.source} is unsupported currently.")
        }
    } catch (e: Exception) {
        send(Resource.Failure(e.message))
    }
}
