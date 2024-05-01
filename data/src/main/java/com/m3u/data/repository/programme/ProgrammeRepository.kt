package com.m3u.data.repository.programme

import androidx.paging.PagingSource
import com.m3u.data.database.model.Programme
import kotlinx.coroutines.flow.StateFlow

interface ProgrammeRepository {
    fun pagingByEpgUrlsAndChannelId(
        epgUrls: List<String>,
        channelId: String
    ): PagingSource<Int, Programme>

    val refreshingEpgUrls: StateFlow<List<String>>
    suspend fun checkOrRefreshProgrammesOrThrow(
        playlistUrl: String,
        ignoreCache: Boolean
    )
}