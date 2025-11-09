package com.m3u.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.m3u.data.database.model.WatchProgress
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(watchProgress: WatchProgress)

    @Query("SELECT * FROM watch_progress WHERE channel_id = :channelId")
    suspend fun getByChannelId(channelId: Int): WatchProgress?

    @Query("SELECT * FROM watch_progress WHERE channel_id = :channelId")
    fun observeByChannelId(channelId: Int): Flow<WatchProgress?>

    // Enterprise-level: Get last started content (all playlists, any progress)
    // Used when Content Type Mode is OFF or for M3U playlists
    @Query("""
        SELECT wp.* FROM watch_progress wp
        INNER JOIN streams s ON wp.channel_id = s.id
        INNER JOIN playlists p ON s.playlist_url = p.url
        ORDER BY wp.last_watched DESC
        LIMIT :limit
    """)
    fun getContinueWatching(limit: Int = 5): Flow<List<WatchProgress>>

    // Enterprise-level: Get last started Movies (Xtream VOD playlists only)
    // Used when Content Type Mode is ON
    @Query("""
        SELECT wp.* FROM watch_progress wp
        INNER JOIN streams s ON wp.channel_id = s.id
        INNER JOIN playlists p ON s.playlist_url = p.url
        WHERE p.url LIKE '%type=vod%'
        ORDER BY wp.last_watched DESC
        LIMIT :limit
    """)
    fun getLastStartedMovies(limit: Int = 5): Flow<List<WatchProgress>>

    // Enterprise-level: Get last started TV Series (Xtream Series playlists only)
    // Used when Content Type Mode is ON
    @Query("""
        SELECT wp.* FROM watch_progress wp
        INNER JOIN streams s ON wp.channel_id = s.id
        INNER JOIN playlists p ON s.playlist_url = p.url
        WHERE p.url LIKE '%type=series%'
        ORDER BY wp.last_watched DESC
        LIMIT :limit
    """)
    fun getLastStartedSeries(limit: Int = 5): Flow<List<WatchProgress>>

    @Query("DELETE FROM watch_progress WHERE channel_id = :channelId")
    suspend fun deleteByChannelId(channelId: Int)

    @Query("DELETE FROM watch_progress")
    suspend fun deleteAll()
}
