package com.m3u.data.repository.programme

import androidx.paging.PagingSource
import com.m3u.data.database.model.Programme
import com.m3u.data.database.model.ProgrammeRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ProgrammeRepository {
    fun pagingByEpgUrlsAndChannelId(
        epgUrls: List<String>,
        channelId: String
    ): PagingSource<Int, Programme>

    fun observeProgrammeRange(
        epgUrls: List<String>,
        channelId: String
    ): Flow<ProgrammeRange>

    val refreshingEpgUrls: StateFlow<List<String>>
    fun checkOrRefreshProgrammesOrThrow(
        playlistUrl: String,
        ignoreCache: Boolean
    ): Flow<Int>
}