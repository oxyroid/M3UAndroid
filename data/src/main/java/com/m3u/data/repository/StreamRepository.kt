package com.m3u.data.repository

import androidx.paging.PagingSource
import com.m3u.data.database.model.Stream
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

interface StreamRepository {
    fun observe(id: Int): Flow<Stream?>
    fun observeAll(): Flow<List<Stream>>
    fun pagingAllByPlaylistUrl(url: String): PagingSource<Int, Stream>
    suspend fun get(id: Int): Stream?

    @Deprecated("stream url is not unique")
    suspend fun getByUrl(url: String): Stream?
    suspend fun getByPlaylistUrl(playlistUrl: String): List<Stream>
    suspend fun setFavourite(id: Int, target: Boolean)
    suspend fun hide(id: Int, target: Boolean)
    suspend fun reportPlayed(id: Int)
    suspend fun getPlayedRecently(): Stream?
    fun observeAllUnseenFavourites(limit: Duration): Flow<List<Stream>>
    fun observeAllFavourite(): Flow<List<Stream>>
    fun observeAllHidden(): Flow<List<Stream>>
}
