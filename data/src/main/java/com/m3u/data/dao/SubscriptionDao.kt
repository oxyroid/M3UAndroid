package com.m3u.data.dao

import androidx.room.*
import com.m3u.data.entity.Subscription
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subscription: Subscription): Long

    @Delete
    suspend fun delete(vararg subscription: Subscription)

    @Query("SELECT * FROM subscriptions")
    suspend fun getAll(): List<Subscription>

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun getById(id: Int): Subscription?

    @Query("SELECT * FROM subscriptions WHERE url = :url")
    suspend fun getByUrl(url: String): Subscription?

    @Query("SELECT * FROM subscriptions")
    fun observeAll(): Flow<List<Subscription>>

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    fun observeById(id: Int): Flow<Subscription?>
}