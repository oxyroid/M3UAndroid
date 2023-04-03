package com.m3u.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.m3u.data.local.entity.Live
import kotlinx.coroutines.flow.Flow

@Dao
interface LiveDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(live: Live)

    @Delete
    suspend fun delete(live: Live)

    @Query("DELETE FROM lives WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM lives WHERE feedUrl = :feedUrl")
    suspend fun deleteByFeedUrl(feedUrl: String)

    @Query("SELECT * FROM lives WHERE id = :id")
    suspend fun get(id: Int): Live?

    @Query("SELECT * FROM lives WHERE url = :url")
    suspend fun getByUrl(url: String): Live?

    @Query("SELECT * FROM lives WHERE feedUrl = :feedUrl")
    suspend fun getByFeedUrl(feedUrl: String): List<Live>

    @Query("SELECT * FROM lives WHERE id = :id")
    fun observeById(id: Int): Flow<Live?>

    @Query("SELECT * FROM lives")
    fun observeAll(): Flow<List<Live>>

    @Query("UPDATE lives SET favourite = :target WHERE id = :id")
    suspend fun setFavouriteLive(id: Int, target: Boolean)

    @Query("UPDATE lives SET banned = :target WHERE id = :id")
    suspend fun setBannedLive(id: Int, target: Boolean)
}