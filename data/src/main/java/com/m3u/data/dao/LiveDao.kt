package com.m3u.data.dao

import androidx.room.*
import com.m3u.data.entity.Live
import kotlinx.coroutines.flow.Flow

@Dao
interface LiveDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(live: Live)

    @Delete
    suspend fun delete(live: Live)

    @Query("DELETE FROM lives WHERE subscriptionId = :oldId")
    suspend fun deleteBySubscriptionId(oldId: Int)

    @Query("SELECT * FROM lives")
    suspend fun getAll(): List<Live>

    @Query("SELECT * FROM lives WHERE id = :id")
    fun observeById(id: Int): Flow<Live?>

    @Query("SELECT * FROM lives")
    fun observeAll(): Flow<List<Live>>

    @Query("SELECT COUNT(*) FROM lives WHERE subscriptionId = :id")
    fun observeCountBySubscriptionId(id: Int): Flow<Int>

    @Query("SELECT * FROM lives WHERE subscriptionId = :id")
    fun observeAllBySubscriptionId(id: Int): Flow<List<Live>>
}