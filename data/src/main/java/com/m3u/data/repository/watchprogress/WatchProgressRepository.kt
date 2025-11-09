package com.m3u.data.repository.watchprogress

import com.m3u.data.database.model.WatchProgress
import kotlinx.coroutines.flow.Flow

interface WatchProgressRepository {
    suspend fun upsert(watchProgress: WatchProgress)
    suspend fun getByChannelId(channelId: Int): WatchProgress?
    fun observeByChannelId(channelId: Int): Flow<WatchProgress?>

    // Enterprise-level: Continue Watching queries
    fun getContinueWatching(limit: Int = 5): Flow<List<WatchProgress>>
    fun getLastStartedMovies(limit: Int = 5): Flow<List<WatchProgress>>
    fun getLastStartedSeries(limit: Int = 5): Flow<List<WatchProgress>>

    suspend fun deleteByChannelId(channelId: Int)
    suspend fun deleteAll()
}
