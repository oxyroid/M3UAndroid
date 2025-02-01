package com.m3u.data.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.m3u.data.database.model.AdjacentChannels
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
            WHERE playlist_url = :playlistUrl
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
            WHERE playlist_url = :playlistUrl
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

    @Query("DELETE FROM streams WHERE playlist_url = :playlistUrl")
    suspend fun deleteByPlaylistUrl(playlistUrl: String)

    @Query("DELETE FROM streams WHERE playlist_url = :playlistUrl AND (favourite = 0 AND hidden = 0)")
    suspend fun deleteByPlaylistUrlIgnoreFavOrHidden(playlistUrl: String)

    @Query("SELECT relation_id FROM streams WHERE relation_id IS NOT NULL AND playlist_url IN (:playlistUrls) AND (favourite = 1 OR hidden = 1)")
    suspend fun getFavOrHiddenRelationIdsByPlaylistUrl(vararg playlistUrls: String): List<String>

    @Query("SELECT url FROM streams WHERE relation_id IS NULL AND playlist_url IN (:playlistUrls) AND (favourite = 1 OR hidden = 1)")
    suspend fun getFavOrHiddenUrlsByPlaylistUrlNotContainsRelationId(vararg playlistUrls: String): List<String>

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
        AND playlist_url NOT IN (:seriesPlaylistUrls)
        ORDER BY RANDOM()
        LIMIT 1
    """
    )
    suspend fun randomIgnoreSeriesAndHidden(vararg seriesPlaylistUrls: String): Channel?

    @Query(
        """
        SELECT * FROM streams
        WHERE favourite = 1
        AND playlist_url NOT IN (:seriesPlaylistUrls)
        ORDER BY RANDOM()
        LIMIT 1
    """
    )
    suspend fun randomIgnoreSeriesInFavourite(vararg seriesPlaylistUrls: String): Channel?

    @Query("SELECT * FROM streams WHERE url = :url AND playlist_url = :playlistUrl")
    suspend fun getByPlaylistUrlAndUrl(playlistUrl: String, url: String): Channel?

    @Query("SELECT * FROM streams WHERE title = :title AND playlist_url = :playlistUrl")
    suspend fun getByPlaylistUrlAndTitle(playlistUrl: String, title: String): Channel?

    @Query("SELECT * FROM streams WHERE playlist_url = :playlistUrl")
    suspend fun getByPlaylistUrl(playlistUrl: String): List<Channel>

    @Query("SELECT * FROM streams WHERE playlist_url = :playlistUrl AND relation_id = :relationId")
    suspend fun getByPlaylistUrlAndRelationId(playlistUrl: String, relationId: String): Channel?

    @Query("SELECT * FROM streams WHERE id = :id")
    fun observeById(id: Int): Flow<Channel?>

    @Query("SELECT * FROM streams WHERE playlist_url = :playlistUrl")
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
            WHERE playlist_url = :url
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
            WHERE playlist_url = :url
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
            WHERE playlist_url = :url
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
            WHERE playlist_url = :url
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

    @Query(
        """
            SELECT * FROM streams 
            WHERE playlist_url = :url
            AND title LIKE '%'||:query||'%'
        """
    )
    fun pagingAllByPlaylistUrlMixed(
        url: String,
        query: String
    ): PagingSource<Int, Channel>

    @Query("SELECT COUNT(playlist_url) FROM streams WHERE playlist_url = :playlistUrl")
    fun observeCountByPlaylistUrl(playlistUrl: String): Flow<Int>

    @Query("SELECT COUNT(playlist_url) FROM streams WHERE playlist_url = :playlistUrl")
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

    @Query(
        """
            WITH TargetChannel AS (
                SELECT *
                FROM streams
                WHERE id = :channelId
                AND playlist_url = :playlistUrl
                AND `group` = :category
            )
            SELECT
                (SELECT id FROM streams
                 WHERE playlist_url = :playlistUrl
                   AND `group` = :category
                   AND title < (SELECT title FROM TargetChannel)
                   ORDER BY title DESC LIMIT 1) AS next_id,
                (SELECT id FROM streams
                 WHERE playlist_url = :playlistUrl
                   AND `group` = :category
                   AND title > (SELECT title FROM TargetChannel)
                   ORDER BY title ASC LIMIT 1) AS prev_id
        """
    )
    fun observeAdjacentChannels(
        channelId: Int,
        playlistUrl: String,
        category: String,
    ): Flow<AdjacentChannels>

}
