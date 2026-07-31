package com.m3u.business.foryou

import androidx.compose.runtime.Immutable
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist

@Immutable
data class HomeFeedSection(
    val id: String,
    val title: String,
    val supporting: String?,
    val playlist: Playlist,
    val channels: List<Channel>,
)
