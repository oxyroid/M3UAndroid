package com.m3u.data.repository.programme

import androidx.paging.PagingSource
import com.m3u.data.database.model.Programme
import kotlinx.coroutines.flow.StateFlow

interface ProgrammeRepository {
    fun pagingByChannelId(channelId: String): PagingSource<Int, Programme>
    val refreshingEpgUrls: StateFlow<List<String>>
    suspend fun checkOrRefreshProgrammesByPlaylistUrlOrThrow(playlistUrl: String, ignoreCache: Boolean)
}