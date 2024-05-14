package com.m3u.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.m3u.data.database.model.ColorScheme
import kotlinx.coroutines.flow.Flow

@Dao
interface ColorPackDao {
    @Query("SELECT * FROM color_pack")
    fun observeAllColorPacks(): Flow<List<ColorScheme>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertColorPack(colorScheme: ColorScheme)

    @Delete
    suspend fun deleteColorPack(colorScheme: ColorScheme)
}