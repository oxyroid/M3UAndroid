package com.m3u.data.repository.channel

import androidx.paging.PagingData
import androidx.paging.PagingSource
import com.m3u.core.wrapper.Sort
import com.m3u.data.database.model.AdjacentChannels
import com.m3u.data.database.model.Channel
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

interface ChannelRepository {
    fun observe(id: Int): Flow<Channel?>

    fun observeAllByPlaylistUrl(playlistUrl: String): Flow<List<Channel>>
    fun pagingAll(query: String): PagingSource<Int, Channel>
    fun pagingAllByPlaylistUrl(
        url: String,
        category: String,
        query: String,
        sort: Sort
    ): PagingSource<Int, Channel>

    suspend fun get(id: Int): Channel?
    fun observeAdjacentChannels(
        channelId: Int,
        playlistUrl: String,
        category: String,
    ): Flow<AdjacentChannels>

    suspend fun getByPlaylistUrl(playlistUrl: String): List<Channel>
    suspend fun favouriteOrUnfavourite(id: Int)
    suspend fun hide(id: Int, target: Boolean)
    suspend fun reportPlayed(id: Int)
    suspend fun getPlayedRecently(): Channel?
    fun observePlayedRecently(): Flow<Channel?>
    fun observeAllUnseenFavorites(limit: Duration): Flow<List<Channel>>
    fun observeAllFavorite(): Flow<List<Channel>>
    fun pagingAllFavorite(sort: Sort): PagingSource<Int, Channel>
    fun observeAllHidden(): Flow<List<Channel>>
    fun search(query: String): PagingSource<Int, Channel>
}
