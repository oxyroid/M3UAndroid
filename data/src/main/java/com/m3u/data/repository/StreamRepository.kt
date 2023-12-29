package com.m3u.data.repository

import com.m3u.data.database.entity.Stream
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

interface StreamRepository : ReadOnlyRepository<Stream, Int> {
    suspend fun getByUrl(url: String): Stream?
    suspend fun getByPlaylistUrl(playlistUrl: String): List<Stream>
    suspend fun setFavourite(id: Int, target: Boolean)
    suspend fun setBanned(id: Int, target: Boolean)
    suspend fun updateSeen(id: Int)
    fun observeAllUnseenFavourites(limit: Duration): Flow<List<Stream>>
}
