package com.m3u.data.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import com.m3u.data.database.model.Episode
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE series_id = :seriesId ORDER BY number")
    fun observeAllBySeriesId(seriesId: Int): Flow<Episode>

    @Query("SELECT * FROM episodes WHERE series_id = :seriesId ORDER BY number")
    fun pagingAllBySeriesId(seriesId: Int): PagingSource<Int, Episode>
}