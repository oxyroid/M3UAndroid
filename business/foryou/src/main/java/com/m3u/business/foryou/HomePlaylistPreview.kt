package com.m3u.business.foryou

import androidx.compose.runtime.Immutable
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist

@Immutable
data class HomePlaylistPreview(
    val playlist: Playlist,
    val channelCount: Int,
    val channels: List<Channel>,
)
