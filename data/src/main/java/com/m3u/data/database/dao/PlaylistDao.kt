package com.m3u.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.PlaylistWithStreams
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: Playlist): Long

    @Delete
    suspend fun delete(vararg playlist: Playlist)

    @Query("DELETE FROM playlists WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("SELECT * FROM playlists WHERE url = :url")
    suspend fun getByUrl(url: String): Playlist?

    @Query("SELECT * FROM playlists ORDER BY title")
    fun observeAll(): Flow<List<Playlist>>

    @Transaction
    @Query("SELECT * FROM playlists ORDER BY title")
    fun observeAllWithStreams(): Flow<List<PlaylistWithStreams>>

    @Transaction
    @Query("SELECT * FROM playlists ORDER BY title")
    suspend fun getAllWithStreams(): List<PlaylistWithStreams>

    @Query("SELECT * FROM playlists WHERE url = :url ORDER BY title")
    fun observeByUrl(url: String): Flow<Playlist?>

    @Transaction
    @Query("SELECT * FROM playlists WHERE url = :url ORDER BY title")
    fun observeByUrlWithStreams(url: String): Flow<PlaylistWithStreams?>

    @Transaction
    @Query("SELECT * FROM playlists WHERE url = :url ORDER BY title")
    suspend fun getByUrlWithStreams(url: String): PlaylistWithStreams?

    @Query("UPDATE playlists SET title = :target WHERE url = :url")
    suspend fun rename(url: String, target: String)

    @Transaction
    suspend fun updateUrl(oldUrl: String, newUrl: String) {
        val playlist = getByUrl(oldUrl) ?: return
        insert(
            Playlist(
                title = playlist.title,
                url = newUrl
            )
        )
        deleteByUrl(oldUrl)
    }
}
