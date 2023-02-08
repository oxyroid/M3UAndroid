package com.m3u.data.dao

import androidx.room.*
import com.m3u.data.entity.Feed
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(feed: Feed): Long

    @Delete
    suspend fun delete(vararg feed: Feed)

    @Query("SELECT * FROM feeds WHERE url = :url")
    suspend fun getByUrl(url: String): Feed?

    @Query("SELECT * FROM feeds")
    fun observeAll(): Flow<List<Feed>>

    @Query("SELECT * FROM feeds WHERE url = :url")
    fun observeByUrl(url: String): Flow<Feed?>
}