package com.m3u.features.playlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import com.m3u.core.wrapper.Event
import com.m3u.core.wrapper.handledEvent
import com.m3u.data.database.entity.Stream

@Immutable
data class Channel(
    val title: String,
    val streams: List<Stream>
)

@Immutable
data class ChannelHolder(
    val channels: List<Channel>,
    val zapping: Stream? = null
)

@Composable
fun rememberChannelHolder(
    channels: List<Channel>,
    zapping: Stream? = null
): ChannelHolder {
    return remember(channels, zapping) {
        ChannelHolder(channels, zapping)
    }
}

data class PlaylistState(
    val scrollUp: Event<Unit> = handledEvent()
)
