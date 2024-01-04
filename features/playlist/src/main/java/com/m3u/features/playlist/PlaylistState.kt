package com.m3u.features.playlist

import androidx.compose.runtime.Immutable
import com.m3u.core.wrapper.Event
import com.m3u.core.wrapper.handledEvent
import com.m3u.data.database.entity.Stream
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class Channel(
    val title: String,
    // keep immutable
    // @see PlaylistPager for reason
    val streams: ImmutableList<Stream>
)

data class PlaylistState(
    val scrollUp: Event<Unit> = handledEvent()
)
