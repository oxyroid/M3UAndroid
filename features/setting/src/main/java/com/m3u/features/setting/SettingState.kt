package com.m3u.features.setting

import com.m3u.core.annotation.ConnectTimeout
import com.m3u.core.annotation.FeedStrategy
import com.m3u.core.architecture.Configuration
import com.m3u.core.wrapper.Event
import com.m3u.core.wrapper.handledEvent

data class SettingState(
    val adding: Boolean = false,
    val title: String = "",
    val url: String = "",
    val message: Event<String> = handledEvent(),
    val version: String = "",
    @FeedStrategy val feedStrategy: Int = Configuration.DEFAULT_FEED_STRATEGY,
    val editMode: Boolean = Configuration.DEFAULT_EDIT_MODE,
    val useCommonUIMode: Boolean = Configuration.DEFAULT_USE_COMMON_UI_MODE,
    @ConnectTimeout val connectTimeout: Int = Configuration.DEFAULT_CONNECT_TIMEOUT,
)