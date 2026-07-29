package com.m3u.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.m3u.data.database.model.ColorScheme
import kotlinx.coroutines.flow.Flow

@Dao
interface ColorSchemeDao {
    @Query("SELECT * FROM color_pack")
    fun observeAll(): Flow<List<ColorScheme>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(colorScheme: ColorScheme)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vararg colorScheme: ColorScheme)

    @Delete
    suspend fun delete(colorScheme: ColorScheme)

    @Transaction
    suspend fun replace(
        previous: ColorScheme?,
        replacement: ColorScheme,
    ) {
        if (previous != null) {
            delete(previous)
        }
        insert(replacement)
    }
}
