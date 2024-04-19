package com.m3u.data.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import com.m3u.data.database.model.Programme
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgrammeDao {
    @Query("SELECT * FROM programmes WHERE stream_id = :streamId ORDER BY start")
    fun observeAllByStreamId(streamId: Int): Flow<Programme>

    @Query("SELECT * FROM programmes WHERE stream_id = :streamId ORDER BY start")
    fun pagingAllByStreamId(streamId: Int): PagingSource<Int, Programme>
}