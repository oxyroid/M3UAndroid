package com.m3u.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.m3u.data.database.entity.Playlist
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: Playlist): Long

    @Delete
    suspend fun delete(vararg playlist: Playlist)

    @Query("SELECT * FROM playlists WHERE url = :url")
    suspend fun getByUrl(url: String): Playlist?

    @Query("SELECT * FROM playlists ORDER BY title")
    fun observeAll(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists WHERE url = :url ORDER BY title")
    fun observeByUrl(url: String): Flow<Playlist?>

    @Query("UPDATE playlists SET title = :target WHERE url = :url")
    suspend fun rename(url: String, target: String)
}
