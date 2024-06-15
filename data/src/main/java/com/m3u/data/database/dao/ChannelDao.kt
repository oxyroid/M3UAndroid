package com.m3u.data.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.m3u.data.database.model.Channel
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ChannelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(channel: Channel): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceAll(vararg channels: Channel)

    @Query(
        """
            SELECT DISTINCT `group`
            FROM streams
            WHERE playlistUrl = :playlistUrl
            AND title LIKE '%'||:query||'%'
        """
    )
    suspend fun getCategoriesByPlaylistUrl(
        playlistUrl: String,
        query: String
    ): List<String>

    @Query(
        """
            SELECT DISTINCT `group`
            FROM streams
            WHERE playlistUrl = :playlistUrl
            AND title LIKE '%'||:query||'%'
        """
    )
    fun observeCategoriesByPlaylistUrl(
        playlistUrl: String,
        query: String
    ): Flow<List<String>>

    @Delete
    suspend fun delete(channel: Channel)

    @Query("DELETE FROM streams WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM streams WHERE playlistUrl = :playlistUrl")
    suspend fun deleteByPlaylistUrl(playlistUrl: String)

    @Query("DELETE FROM streams WHERE playlistUrl = :playlistUrl AND (favourite = 0 AND hidden = 0)")
    suspend fun deleteByPlaylistUrlIgnoreFavOrHidden(playlistUrl: String)

    @Query("SELECT channel_id FROM streams WHERE channel_id IS NOT NULL AND playlistUrl IN (:playlistUrls) AND (favourite = 1 OR hidden = 1)")
    suspend fun getFavOrHiddenOriginalIdsByPlaylistUrl(vararg playlistUrls: String): List<String>

    @Query("SELECT url FROM streams WHERE channel_id IS NULL AND playlistUrl IN (:playlistUrls) AND (favourite = 1 OR hidden = 1)")
    suspend fun getFavOrHiddenUrlsByPlaylistUrlNotContainsOriginalId(vararg playlistUrls: String): List<String>

    @Query("SELECT * FROM streams WHERE seen != 0 ORDER BY seen DESC LIMIT 1")
    suspend fun getPlayedRecently(): Channel?

    @Query("SELECT * FROM streams WHERE seen != 0 ORDER BY seen DESC LIMIT 1")
    fun observePlayedRecently(): Flow<Channel?>

    @Query("SELECT * FROM streams WHERE id = :id")
    suspend fun get(id: Int): Channel?

    @Query(
        """
        SELECT * FROM streams
        WHERE hidden = 0
        AND playlistUrl NOT IN (:seriesPlaylistUrls)
        ORDER BY RANDOM()
        LIMIT 1
    """
    )
    suspend fun randomIgnoreSeriesAndHidden(vararg seriesPlaylistUrls: String): Channel?

    @Query(
        """
        SELECT * FROM streams
        WHERE favourite = 1
        AND playlistUrl NOT IN (:seriesPlaylistUrls)
        ORDER BY RANDOM()
        LIMIT 1
    """
    )
    suspend fun randomIgnoreSeriesInFavourite(vararg seriesPlaylistUrls: String): Channel?

    @Query("SELECT * FROM streams WHERE url = :url AND playlistUrl = :playlistUrl")
    suspend fun getByPlaylistUrlAndUrl(playlistUrl: String, url: String): Channel?

    @Query("SELECT * FROM streams WHERE title = :title AND playlistUrl = :playlistUrl")
    suspend fun getByPlaylistUrlAndTitle(playlistUrl: String, title: String): Channel?

    @Query("SELECT * FROM streams WHERE playlistUrl = :playlistUrl")
    suspend fun getByPlaylistUrl(playlistUrl: String): List<Channel>

    @Query("SELECT * FROM streams WHERE playlistUrl = :playlistUrl AND channel_id = :originalId")
    suspend fun getByPlaylistUrlAndOriginalId(playlistUrl: String, originalId: String): Channel?

    @Query("SELECT * FROM streams WHERE id = :id")
    fun observeById(id: Int): Flow<Channel?>

    @Query("SELECT * FROM streams WHERE playlistUrl = :playlistUrl")
    fun observeAllByPlaylistUrl(playlistUrl: String): Flow<List<Channel>>

    @Query("SELECT * FROM streams WHERE hidden = 0")
    fun observeAllUnhidden(): Flow<List<Channel>>

    @Query("SELECT * FROM streams WHERE favourite = 1")
    fun observeAllFavourite(): Flow<List<Channel>>

    @Query("SELECT * FROM streams WHERE hidden = 1")
    fun observeAllHidden(): Flow<List<Channel>>

    @Query(
        """
            SELECT * FROM streams 
            WHERE playlistUrl = :url
            AND title LIKE '%'||:query||'%'
            AND `group` = :category
        """
    )
    fun pagingAllByPlaylistUrl(
        url: String,
        category: String,
        query: String
    ): PagingSource<Int, Channel>

    @Query(
        """
            SELECT * FROM streams 
            WHERE playlistUrl = :url
            AND title LIKE '%'||:query||'%'
            AND `group` = :category
            ORDER BY title ASC
        """
    )
    fun pagingAllByPlaylistUrlAsc(
        url: String,
        category: String,
        query: String
    ): PagingSource<Int, Channel>

    @Query(
        """
            SELECT * FROM streams 
            WHERE playlistUrl = :url
            AND title LIKE '%'||:query||'%'
            AND `group` = :category
            ORDER BY title DESC
        """
    )
    fun pagingAllByPlaylistUrlDesc(
        url: String,
        category: String,
        query: String
    ): PagingSource<Int, Channel>

    @Query(
        """
            SELECT * FROM streams 
            WHERE playlistUrl = :url
            AND title LIKE '%'||:query||'%'
            AND `group` = :category
            ORDER BY seen DESC
        """
    )
    fun pagingAllByPlaylistUrlRecently(
        url: String,
        category: String,
        query: String
    ): PagingSource<Int, Channel>

    @Query("SELECT COUNT(playlistUrl) FROM streams WHERE playlistUrl = :playlistUrl")
    fun observeCountByPlaylistUrl(playlistUrl: String): Flow<Int>

    @Query("SELECT COUNT(playlistUrl) FROM streams WHERE playlistUrl = :playlistUrl")
    suspend fun getCountByPlaylistUrl(playlistUrl: String): Int

    @Query("SELECT * FROM streams WHERE favourite = 1 AND seen + :limit < :current ORDER BY seen")
    fun observeAllUnseenFavourites(
        limit: Long,
        current: Long
    ): Flow<List<Channel>>

    @Query("UPDATE streams SET favourite = :target WHERE id = :id")
    suspend fun favouriteOrUnfavourite(id: Int, target: Boolean)

    @Query("UPDATE streams SET hidden = :target WHERE id = :id")
    suspend fun hide(id: Int, target: Boolean)

    @Query("UPDATE streams SET seen = :target WHERE id = :id")
    suspend fun updateSeen(id: Int, target: Long)
}
