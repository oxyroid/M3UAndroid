package com.m3u.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.m3u.data.database.model.ChannelDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDetailsDao {
    @Upsert
    suspend fun upsert(details: ChannelDetails)

    @Query(
        """
            SELECT * FROM channel_details
            WHERE playlist_url = :playlistUrl AND channel_reference = :channelReference
        """
    )
    suspend fun get(playlistUrl: String, channelReference: String): ChannelDetails?

    @Query(
        """
            SELECT * FROM channel_details
            WHERE playlist_url = :playlistUrl AND channel_reference = :channelReference
        """
    )
    fun observe(playlistUrl: String, channelReference: String): Flow<ChannelDetails?>

    /**
     * References already fetched for a playlist, so the background sweep can
     * skip them without asking for each one in turn.
     */
    @Query("SELECT channel_reference FROM channel_details WHERE playlist_url = :playlistUrl")
    suspend fun getFetchedReferences(playlistUrl: String): List<String>

    @Query("SELECT COUNT(*) FROM channel_details WHERE playlist_url = :playlistUrl")
    suspend fun countByPlaylistUrl(playlistUrl: String): Int

    /**
     * Called when a playlist is unsubscribed for good.
     *
     * This table carries no cascading foreign key on purpose — cascading would
     * also fire on the INSERT OR REPLACE a refresh performs, discarding
     * descriptions that cost one request each to collect.
     */
    @Query("DELETE FROM channel_details WHERE playlist_url = :playlistUrl")
    suspend fun deleteByPlaylistUrl(playlistUrl: String)
}
