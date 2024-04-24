package com.m3u.data.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.m3u.data.database.model.Stream
import kotlinx.coroutines.flow.Flow

@Dao
internal interface StreamDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(stream: Stream): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceAll(vararg streams: Stream)

    @Delete
    suspend fun delete(stream: Stream)

    @Query("DELETE FROM streams WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM streams WHERE playlistUrl = :playlistUrl")
    suspend fun deleteByPlaylistUrl(playlistUrl: String)

    @Query("DELETE FROM streams WHERE playlistUrl = :playlistUrl AND (favourite = 0 AND hidden = 0)")
    suspend fun deleteByPlaylistUrlIgnoreFavOrHidden(playlistUrl: String)

    @Query("SELECT channel_id FROM streams WHERE channel_id IS NOT NULL AND playlistUrl IN (:playlistUrls) AND (favourite = 1 OR hidden = 1)")
    suspend fun getFavOrHiddenChannelIdsByPlaylistUrl(vararg playlistUrls: String): List<String>

    @Query("SELECT url FROM streams WHERE channel_id IS NULL AND playlistUrl IN (:playlistUrls) AND (favourite = 1 OR hidden = 1)")
    suspend fun getFavOrHiddenUrlsByPlaylistUrlNotContainsChannelId(vararg playlistUrls: String): List<String>

    @Query("SELECT * FROM streams WHERE seen != 0 ORDER BY seen DESC LIMIT 1")
    suspend fun getPlayedRecently(): Stream?

    @Query("SELECT * FROM streams WHERE id = :id")
    suspend fun get(id: Int): Stream?

    @Query("SELECT * FROM streams WHERE url = :url")
    suspend fun getByUrl(url: String): Stream?

    @Query("SELECT * FROM streams WHERE url = :url AND playlistUrl = :playlistUrl")
    suspend fun getByPlaylistUrlAndUrl(playlistUrl: String, url: String): Stream?

    @Query("SELECT * FROM streams WHERE title = :title AND playlistUrl = :playlistUrl")
    suspend fun getByPlaylistUrlAndTitle(playlistUrl: String, title: String): Stream?

    @Query("SELECT * FROM streams WHERE playlistUrl = :playlistUrl")
    suspend fun getByPlaylistUrl(playlistUrl: String): List<Stream>

    @Query("SELECT * FROM streams WHERE playlistUrl = :playlistUrl AND channel_id = :channelId")
    suspend fun getByPlaylistUrlAndChannelId(playlistUrl: String, channelId: String): Stream?

    @Query("SELECT * FROM streams WHERE id = :id")
    fun observeById(id: Int): Flow<Stream?>

    @Query("SELECT * FROM streams WHERE playlistUrl = :playlistUrl")
    fun observeAllByPlaylistUrl(playlistUrl: String): Flow<List<Stream>>

    @Query("SELECT * FROM streams WHERE hidden = 0")
    fun observeAllUnhidden(): Flow<List<Stream>>

    @Query("SELECT * FROM streams WHERE favourite = 1")
    fun observeAllFavourite(): Flow<List<Stream>>

    @Query("SELECT * FROM streams WHERE hidden = 1")
    fun observeAllHidden(): Flow<List<Stream>>

    @Query("SELECT * FROM streams WHERE playlistUrl = :url AND title LIKE '%'||:query||'%'")
    fun pagingAllByPlaylistUrl(
        url: String,
        query: String
    ): PagingSource<Int, Stream>

    @Query("SELECT * FROM streams WHERE playlistUrl = :url AND title LIKE '%'||:query||'%' ORDER BY title ASC")
    fun pagingAllByPlaylistUrlAsc(
        url: String,
        query: String
    ): PagingSource<Int, Stream>

    @Query("SELECT * FROM streams WHERE playlistUrl = :url AND title LIKE '%'||:query||'%' ORDER BY title DESC")
    fun pagingAllByPlaylistUrlDesc(
        url: String,
        query: String
    ): PagingSource<Int, Stream>

    @Query("SELECT * FROM streams WHERE playlistUrl = :url AND title LIKE '%'||:query||'%' ORDER BY seen DESC")
    fun pagingAllByPlaylistUrlRecently(
        url: String,
        query: String
    ): PagingSource<Int, Stream>

    @Query("SELECT COUNT(playlistUrl) FROM streams WHERE playlistUrl = :playlistUrl")
    fun observeCountByPlaylistUrl(playlistUrl: String): Flow<Int>

    @Query("SELECT COUNT(playlistUrl) FROM streams WHERE playlistUrl = :playlistUrl")
    suspend fun getCountByPlaylistUrl(playlistUrl: String): Int

    @Query("SELECT * FROM streams WHERE favourite = 1 AND seen + :limit < :current ORDER BY seen")
    fun observeAllUnseenFavourites(
        limit: Long,
        current: Long
    ): Flow<List<Stream>>

    @Query("UPDATE streams SET favourite = :target WHERE id = :id")
    suspend fun favouriteOrUnfavourite(id: Int, target: Boolean)

    @Query("UPDATE streams SET hidden = :target WHERE id = :id")
    suspend fun hide(id: Int, target: Boolean)

    @Query("UPDATE streams SET seen = :target WHERE id = :id")
    suspend fun updateSeen(id: Int, target: Long)
}
