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
    val floating: Stream? = null
)

@Composable
fun rememberChannelHolder(
    channels: List<Channel>,
    floating: Stream? = null
): ChannelHolder {
    return remember(channels, floating) {
        ChannelHolder(channels, floating)
    }
}

data class PlaylistState(
    val url: String = "",
    val channels: List<Channel> = emptyList(),
    val fetching: Boolean = false,
    val scrollUp: Event<Unit> = handledEvent()
)
