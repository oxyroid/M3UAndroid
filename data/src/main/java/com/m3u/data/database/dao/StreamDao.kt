package com.m3u.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.m3u.data.database.entity.Stream
import kotlinx.coroutines.flow.Flow

@Dao
interface StreamDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stream: Stream)

    @Delete
    suspend fun delete(stream: Stream)

    @Query("DELETE FROM streams WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM streams WHERE playlistUrl = :playlistUrl")
    suspend fun deleteByPlaylistUrl(playlistUrl: String)

    @Query("SELECT * FROM streams WHERE id = :id")
    suspend fun get(id: Int): Stream?

    @Query("SELECT * FROM streams WHERE url = :url")
    suspend fun getByUrl(url: String): Stream?

    @Query("SELECT * FROM streams WHERE playlistUrl = :playlistUrl ORDER BY id")
    suspend fun getByPlaylistUrl(playlistUrl: String): List<Stream>

    @Query("SELECT * FROM streams WHERE id = :id")
    fun observeById(id: Int): Flow<Stream?>

    @Query("SELECT * FROM streams ORDER BY id")
    fun observeAll(): Flow<List<Stream>>

    @Query("UPDATE streams SET favourite = :target WHERE id = :id")
    suspend fun setFavourite(id: Int, target: Boolean)

    @Query("UPDATE streams SET banned = :target WHERE id = :id")
    suspend fun setBanned(id: Int, target: Boolean)
}
