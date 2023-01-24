package com.m3u.data.dao

import androidx.room.*
import com.m3u.data.entity.Subscription
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg subscription: Subscription)

    @Delete
    suspend fun delete(vararg subscription: Subscription)

    @Query("SELECT * FROM subscriptions")
    suspend fun getAll(): List<Subscription>

    @Query("SELECT * FROM subscriptions")
    fun observeAll(): Flow<List<Subscription>>
}