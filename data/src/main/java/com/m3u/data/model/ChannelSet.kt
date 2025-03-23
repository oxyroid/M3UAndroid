package com.m3u.data.model

import androidx.compose.runtime.Immutable
import com.m3u.core.wrapper.Sort

@Immutable
data class ChannelSet(
    val playlistUrl: String,
    val query: String? = null,
    val sort: Sort = Sort.UNSPECIFIED,
    val category: String? = null
)
