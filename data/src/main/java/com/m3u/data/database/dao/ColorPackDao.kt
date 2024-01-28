package com.m3u.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.m3u.data.database.model.ColorPack
import kotlinx.coroutines.flow.Flow

@Dao
interface ColorPackDao {
    @Query("SELECT * FROM color_pack")
    fun observeAllColorPacks(): Flow<List<ColorPack>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertColorPack(colorPack: ColorPack)

    @Delete
    suspend fun deleteColorPack(colorPack: ColorPack)
}