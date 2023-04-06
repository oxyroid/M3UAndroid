package com.m3u.features.feed

import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.wrapper.Event
import com.m3u.core.wrapper.handledEvent
import com.m3u.data.database.entity.Live

internal typealias MappedLives = Map<String, List<Live>>

data class FeedState(
    val url: String = "",
    val title: String? = null,
    val rowCount: Int = Configuration.DEFAULT_ROW_COUNT,
    val lives: MappedLives = emptyMap(),
    val query: String = "",
    val fetching: Boolean = false,
    val scrollUp: Event<Unit> = handledEvent(),
    val message: Event<String> = handledEvent(),
    val useCommonUIMode: Boolean = Configuration.DEFAULT_USE_COMMON_UI_MODE,
    val scrollMode: Boolean = Configuration.DEFAULT_SCROLL_MODE,
    val godMode: Boolean = Configuration.DEFAULT_GOD_MODE,
    val autoRefresh: Boolean = Configuration.DEFAULT_AUTO_REFRESH
)
