package com.m3u.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.PlaylistWithCount
import com.m3u.data.database.model.PlaylistWithChannels
import kotlinx.coroutines.flow.Flow

@Dao
internal interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(playlist: Playlist): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceAll(vararg playlists: Playlist)

    @Delete
    suspend fun delete(vararg playlist: Playlist)

    @Query("DELETE FROM playlists WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("SELECT * FROM playlists WHERE url = :url")
    suspend fun get(url: String): Playlist?

    @Query("SELECT * FROM playlists WHERE source = :source")
    suspend fun getBySource(source: DataSource): List<Playlist>

    @Query("SELECT * FROM playlists ORDER BY title")
    fun observeAll(): Flow<List<Playlist>>

    @Query("""SELECT * FROM playlists WHERE source = "epg" ORDER BY title""")
    fun observeAllEpgs(): Flow<List<Playlist>>

    @Query("SELECT url FROM playlists ORDER BY title")
    fun observePlaylistUrls(): Flow<List<String>>

    @Transaction
    @Query("SELECT * FROM playlists ORDER BY title")
    fun observeAllWithChannels(): Flow<List<PlaylistWithChannels>>

    @Query("SELECT * FROM playlists ORDER BY title")
    suspend fun getAll(): List<Playlist>

    @Query("SELECT * FROM playlists WHERE auto_refresh_programmes = 1 ORDER BY title")
    suspend fun getAllAutoRefresh(): List<Playlist>

    @Transaction
    @Query("SELECT * FROM playlists ORDER BY title")
    suspend fun getAllWithChannels(): List<PlaylistWithChannels>

    @Query("SELECT * FROM playlists WHERE url = :url ORDER BY title")
    fun observeByUrl(url: String): Flow<Playlist?>

    @Transaction
    @Query("SELECT * FROM playlists WHERE url = :url ORDER BY title")
    fun observeByUrlWithChannels(url: String): Flow<PlaylistWithChannels?>

    @Transaction
    @Query(
        """
        SELECT playlists.*, COUNT(streams.id) AS count 
        FROM playlists 
        LEFT JOIN streams 
        ON playlists.url = streams.playlistUrl 
        WHERE source != "epg" GROUP BY playlists.url
        """
    )
    fun observeAllCounts(): Flow<List<PlaylistWithCount>>

    @Transaction
    @Query("SELECT * FROM playlists WHERE url = :url ORDER BY title")
    suspend fun getByUrlWithChannels(url: String): PlaylistWithChannels?

    @Query("UPDATE playlists SET title = :target WHERE url = :url")
    suspend fun updateTitle(url: String, target: String)

    @Transaction
    suspend fun updateUrl(oldUrl: String, newUrl: String) {
        val playlist = get(oldUrl) ?: return
        insertOrReplace(
            playlist.copy(
                url = newUrl
            )
        )
        // because the url is the primary key so we should delete it manual.
        deleteByUrl(oldUrl)
    }

    @Transaction
    suspend fun updatePinnedCategories(url: String, updater: (List<String>) -> List<String>) {
        val playlist = get(url) ?: return
        insertOrReplace(
            playlist.copy(
                pinnedCategories = updater(playlist.pinnedCategories)
            )
        )
    }

    @Transaction
    suspend fun updateEpgUrls(url: String, updater: (List<String>) -> List<String>) {
        val playlist = get(url) ?: return
        insertOrReplace(
            playlist.copy(
                epgUrls = updater(playlist.epgUrls)
            )
        )
    }

    @Transaction
    suspend fun hideOrUnhideCategory(url: String, category: String) {
        val playlist = get(url) ?: return
        val prev = playlist.hiddenCategories
        insertOrReplace(
            playlist.copy(
                hiddenCategories = if (category in prev) prev - category
                else prev + category
            )
        )
    }

    @Transaction
    suspend fun updateUserAgent(url: String, userAgent: String?) {
        val playlist = get(url) ?: return
        insertOrReplace(
            playlist.copy(
                userAgent = userAgent
            )
        )
    }

    @Transaction
    suspend fun removeEpgUrlForAllPlaylists(epgUrl: String) {
        getAll().forEach { playlist ->
            if (epgUrl in playlist.epgUrls) {
                updateEpgUrls(playlist.url) { prev ->
                    prev - epgUrl
                }
            }
        }
    }

    @Query("UPDATE playlists SET auto_refresh_programmes = :autoRefreshProgrammes WHERE url = :playlistUrl")
    suspend fun updatePlaylistAutoRefreshProgrammes(
        playlistUrl: String,
        autoRefreshProgrammes: Boolean
    )
}
