package com.m3u.data.repository.programme

import androidx.paging.PagingSource
import com.m3u.data.database.model.Programme
import com.m3u.data.database.model.ProgrammeSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ProgrammeRepository {
    // that will only observe playlists whose epgUrl is not null or empty
    fun observeSnapshotsGroupedByPlaylistUrl(): Flow<List<ProgrammeSnapshot>>
    fun pagingAllByStreamId(streamId: Int): PagingSource<Int, Programme>
    suspend fun fetchProgrammesOrThrow(playlistUrl: String)
    val refreshingPlaylistUrls: StateFlow<List<String>>
}