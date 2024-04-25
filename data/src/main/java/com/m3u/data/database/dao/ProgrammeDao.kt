package com.m3u.data.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.m3u.data.database.model.Programme
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgrammeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(programme: Programme)

    @Query(
        """
        SELECT id 
        FROM programmes 
        WHERE 
            epg_url = :epgUrl
            AND 
            channel_id = :channelId 
            AND 
            start = :start
            AND
            `end` = :end
        IS NOT NULL
    """
    )
    suspend fun contains(epgUrl: String, channelId: String, start: Long, end: Long): Boolean

    @Query("""SELECT MAX("end") from programmes WHERE epg_url = :epgUrl""")
    suspend fun getMaxEndByEpgUrl(epgUrl: String): Long?

    @Query("SELECT * FROM programmes WHERE channel_id = :channelId ORDER BY start")
    fun pagingAllByChannelId(channelId: String): PagingSource<Int, Programme>

    @Query("DELETE FROM programmes WHERE epg_url = :epgUrl AND `end` < :startEdge")
    suspend fun cleanByEpgUrlAndStartEdge(epgUrl: String, startEdge: Long)

    @Query("SELECT * FROM programmes ORDER BY start")
    fun observeAll(): Flow<List<Programme>>

    @Query("DELETE FROM programmes WHERE epg_url = :epgUrl")
    suspend fun deleteAllByEpgUrl(epgUrl: String)
}