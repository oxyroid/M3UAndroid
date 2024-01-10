package com.m3u.data.repository

import com.m3u.data.database.model.Stream
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

interface StreamRepository : ReadOnlyRepository<Stream, Int> {
    suspend fun getByUrl(url: String): Stream?
    suspend fun getByPlaylistUrl(playlistUrl: String): List<Stream>
    suspend fun setFavourite(id: Int, target: Boolean)
    suspend fun ban(id: Int, target: Boolean)
    suspend fun reportPlayed(id: Int)
    suspend fun getPlayedRecently(): Stream?
    fun observeAllUnseenFavourites(limit: Duration): Flow<List<Stream>>
}
