package com.m3u.data.repository.programme

import androidx.paging.PagingData
import com.m3u.data.database.model.Programme
import com.m3u.data.database.model.ProgrammeRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ProgrammeRepository {
    fun pagingProgrammes(
        playlistUrl: String,
        relationId: String
    ): Flow<PagingData<Programme>>

    fun observeProgrammeRange(
        playlistUrl: String,
        relationId: String
    ): Flow<ProgrammeRange>

    fun observeProgrammeRange(
        playlistUrl: String
    ): Flow<ProgrammeRange>

    val refreshingEpgUrls: StateFlow<List<String>>
    fun checkOrRefreshProgrammesOrThrow(
        vararg playlistUrls: String,
        ignoreCache: Boolean
    ): Flow<Int>

    suspend fun getById(id: Int): Programme?
    suspend fun getProgrammeCurrently(channelId: Int): Programme?
    suspend fun getProgrammesInTimeRange(
        epgUrls: List<String>,
        relationId: String,
        startTime: Long,
        endTime: Long,
        limit: Int = 10
    ): List<Programme>
}