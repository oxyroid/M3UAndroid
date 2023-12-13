package com.m3u.features.foryou.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import com.m3u.data.database.entity.Playlist

@Immutable
internal data class PlaylistDetail(
    val playlist: Playlist,
    val count: Int
) {
    companion object {
        const val DEFAULT_COUNT = 0
    }
}

@Immutable
internal data class PlaylistDetailHolder(
    val details: List<PlaylistDetail> = emptyList()
)

@Composable
internal fun rememberPlaylistDetailHolder(details: List<PlaylistDetail>): PlaylistDetailHolder {
    return remember(details) {
        PlaylistDetailHolder(details)
    }
}

internal fun Playlist.toDetail(
    count: Int = PlaylistDetail.DEFAULT_COUNT
): PlaylistDetail = PlaylistDetail(
    playlist = this,
    count = count
)