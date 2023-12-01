package com.m3u.features.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.architecture.configuration.ExperimentalConfiguration
import com.m3u.core.wrapper.Event
import com.m3u.core.wrapper.handledEvent
import com.m3u.data.database.entity.Live

@Immutable
data class Channel(
    val title: String,
    val lives: List<Live>
)

@Immutable
data class ChannelHolder(
    val channels: List<Channel>
)

@Composable
fun rememberChannelHolder(channels: List<Channel>): ChannelHolder {
    return remember(channels) {
        ChannelHolder(channels)
    }
}

@OptIn(ExperimentalConfiguration::class)
data class FeedState(
    val url: String = "",
    val channels: List<Channel> = emptyList(),
    val query: String = "",
    val fetching: Boolean = false,
    val scrollUp: Event<Unit> = handledEvent(),
    val error: Event<FeedMessage> = handledEvent(),
    private val configuration: Configuration
) {
    val strategy: Int by configuration.feedStrategy
    var rowCount: Int by configuration.rowCount
    val useCommonUIMode: Boolean by configuration.useCommonUIMode
    val scrollMode: Boolean by configuration.scrollMode
    val godMode: Boolean by configuration.godMode
    val autoRefresh: Boolean by configuration.autoRefresh
    val noPictureMode: Boolean by configuration.noPictureMode
}
