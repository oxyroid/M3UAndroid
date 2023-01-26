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

    @Query("DELETE FROM lives WHERE subscriptionUrl = :subscriptionUrl")
    suspend fun deleteBySubscriptionUrl(subscriptionUrl: String)

    @Query("SELECT * FROM lives WHERE subscriptionUrl = :subscriptionUrl")
    suspend fun getBySubscriptionId(subscriptionUrl: String): List<Live>

    @Query("SELECT * FROM lives WHERE id = :id")
    fun observeById(id: Int): Flow<Live?>

    @Query("SELECT * FROM lives")
    fun observeAll(): Flow<List<Live>>

    @Query("SELECT * FROM lives WHERE subscriptionUrl = :subscriptionUrl")
    fun observeLivesBySubscriptionUrl(subscriptionUrl: String): Flow<List<Live>>
}