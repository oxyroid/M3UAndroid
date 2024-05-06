package com.m3u.data.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.m3u.data.database.model.Programme
import com.m3u.data.database.model.ProgrammeRange
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgrammeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(programme: Programme)

    @Query("""SELECT MAX("end") from programmes WHERE epg_url = :epgUrl""")
    suspend fun getMaxEndByEpgUrl(epgUrl: String): Long?

    @Query(
        """
        SELECT * FROM programmes 
        WHERE epg_url in (:epgUrls) 
        AND 
        channel_id = :channelId
        ORDER BY start
        """
    )
    fun pagingByEpgUrlsAndChannelId(
        epgUrls: List<String>,
        channelId: String
    ): PagingSource<Int, Programme>

    @Query("DELETE FROM programmes WHERE epg_url = :epgUrl")
    suspend fun cleanByEpgUrl(epgUrl: String)

    @Query("SELECT * FROM programmes ORDER BY start")
    fun observeAll(): Flow<List<Programme>>

    @Query("DELETE FROM programmes WHERE epg_url = :epgUrl")
    suspend fun deleteAllByEpgUrl(epgUrl: String)

    @Query(
        """
        SELECT * FROM programmes 
        WHERE epg_url in (:epgUrls) 
        AND channel_id = :channelId
        AND start <= :time
        AND `end` >= :time
        """
    )
    suspend fun getCurrentByEpgUrlsAndChannelId(
        epgUrls: List<String>,
        channelId: String,
        time: Long
    ): Programme?

    @Query(
        """
        SELECT MIN(start) AS start_edge, MAX(`end`) AS end_edge
        FROM programmes
        WHERE epg_url in (:epgUrls)
        AND channel_id = :channelId
        """
    )
    fun observeProgrammeRange(
        epgUrls: List<String>,
        channelId: String
    ): Flow<ProgrammeRange>

    @Query(
        """
        SELECT MIN(start) AS start_edge, MAX(`end`) AS end_edge
        FROM programmes
        WHERE epg_url in (:epgUrls)
        """
    )
    fun observeProgrammeRange(
        epgUrls: List<String>
    ): Flow<ProgrammeRange>
}