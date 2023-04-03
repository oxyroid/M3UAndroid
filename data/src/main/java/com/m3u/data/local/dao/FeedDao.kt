package com.m3u.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.m3u.data.local.entity.Feed
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