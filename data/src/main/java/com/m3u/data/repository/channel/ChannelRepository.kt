package com.m3u.data.repository.channel

import androidx.paging.PagingSource
import com.m3u.data.database.model.Channel
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

interface ChannelRepository {
    fun observe(id: Int): Flow<Channel?>

    fun observeAllByPlaylistUrl(playlistUrl: String): Flow<List<Channel>>
    fun pagingAllByPlaylistUrl(
        url: String,
        category: String,
        query: String,
        sort: Sort
    ): PagingSource<Int, Channel>

    suspend fun get(id: Int): Channel?

    suspend fun getRandomIgnoreSeriesAndHidden(): Channel?

    suspend fun getByPlaylistUrl(playlistUrl: String): List<Channel>
    suspend fun favouriteOrUnfavourite(id: Int)
    suspend fun hide(id: Int, target: Boolean)
    suspend fun reportPlayed(id: Int)
    suspend fun getPlayedRecently(): Channel?
    fun observePlayedRecently(): Flow<Channel?>
    fun observeAllUnseenFavourites(limit: Duration): Flow<List<Channel>>
    fun observeAllFavourite(): Flow<List<Channel>>
    fun observeAllHidden(): Flow<List<Channel>>

    enum class Sort {
        UNSPECIFIED,
        ASC,
        DESC,
        RECENTLY
    }
}
