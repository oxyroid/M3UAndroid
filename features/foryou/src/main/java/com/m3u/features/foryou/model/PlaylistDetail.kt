package com.m3u.features.foryou.model

import androidx.compose.runtime.Immutable
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

internal fun Playlist.toDetail(
    count: Int = PlaylistDetail.DEFAULT_COUNT
): PlaylistDetail = PlaylistDetail(
    playlist = this,
    count = count
)