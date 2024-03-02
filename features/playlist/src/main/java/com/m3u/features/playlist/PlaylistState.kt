package com.m3u.features.playlist

import androidx.compose.runtime.Immutable
import com.m3u.core.wrapper.Event
import com.m3u.core.wrapper.handledEvent
import com.m3u.data.database.model.Stream
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class Group(
    val name: String,
    val streams: ImmutableList<Stream>
)

data class PlaylistState(
    val scrollUp: Event<Unit> = handledEvent()
)
