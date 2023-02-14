package com.m3u.features.setting

import com.m3u.core.architecture.Configuration
import com.m3u.core.wrapper.Event
import com.m3u.core.wrapper.handledEvent

data class SettingState(
    val adding: Boolean = false,
    val title: String = "",
    val url: String = "",
    val message: Event<String> = handledEvent(),
    val version: String = "",
    val feedStrategy: Int = Configuration.DEFAULT_FEED_STRATEGY,
    val useCommonUIMode: Boolean = Configuration.DEFAULT_USE_COMMON_UI_MODE,
    val showMutedAsFeed: Boolean = Configuration.DEFAULT_SHOW_MUTED_AS_FEED
)