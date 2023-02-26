package com.m3u.features.setting

import com.m3u.core.annotation.ClipMode
import com.m3u.core.annotation.ConnectTimeout
import com.m3u.core.annotation.FeedStrategy
import com.m3u.core.architecture.Configuration
import com.m3u.core.wrapper.Event
import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.handledEvent
import com.m3u.data.local.entity.Live
import com.m3u.data.local.entity.Release

data class SettingState(
    val adding: Boolean = false,
    val title: String = "",
    val url: String = "",
    val mutedLives: List<Live> = emptyList(),
    val message: Event<String> = handledEvent(),
    val version: String = "",
    val latestRelease: Resource<Release> = Resource.Loading,
    @FeedStrategy val feedStrategy: Int = Configuration.DEFAULT_FEED_STRATEGY,
    val editMode: Boolean = Configuration.DEFAULT_EDIT_MODE,
    val useCommonUIMode: Boolean = Configuration.DEFAULT_USE_COMMON_UI_MODE,
    @ConnectTimeout val connectTimeout: Int = Configuration.DEFAULT_CONNECT_TIMEOUT,
    val experimentalMode: Boolean = Configuration.DEFAULT_EXPERIMENTAL_MODE,
    @ClipMode val clipMode: Int = Configuration.DEFAULT_CLIP_MODE
)