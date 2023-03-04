package com.m3u.features.feed

import com.m3u.core.architecture.Configuration
import com.m3u.core.wrapper.Event
import com.m3u.core.wrapper.handledEvent
import com.m3u.data.local.entity.Live

data class FeedState(
    val url: String = "",
    val title: String = "",
    val rowCount: Int = Configuration.DEFAULT_ROW_COUNT,
    val lives: Map<String, List<Live>> = emptyMap(),
    val query: String = "",
    val fetching: Boolean = false,
    val scrollUp: Event<Unit> = handledEvent(),
    val message: Event<String> = handledEvent(),
    val useCommonUIMode: Boolean = Configuration.DEFAULT_USE_COMMON_UI_MODE,
    val scrollMode: Boolean = Configuration.DEFAULT_SCROLL_MODE,
    val editMode: Boolean = Configuration.DEFAULT_EDIT_MODE
)
