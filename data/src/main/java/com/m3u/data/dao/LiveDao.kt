package com.m3u.data.dao

import androidx.room.*
import com.m3u.data.entity.Live
import kotlinx.coroutines.flow.Flow

@Dao
interface LiveDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg live: Live)

    @Delete
    suspend fun delete(vararg live: Live)

    @Query("SELECT * FROM lives")
    suspend fun getAll(): List<Live>

    @Query("SELECT * FROM lives")
    fun observeAll(): Flow<List<Live>>
}