package com.m3u.features.setting

import com.m3u.core.annotation.ClipMode
import com.m3u.core.annotation.ConnectTimeout
import com.m3u.core.annotation.FeedStrategy
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.data.database.entity.Live

data class SettingState(
    val version: String = "",
    val adding: Boolean = false,
    val title: String = "",
    val url: String = "",
    val mutedLives: List<Live> = emptyList(),
    @FeedStrategy val feedStrategy: Int = Configuration.DEFAULT_FEED_STRATEGY,
    val godMode: Boolean = Configuration.DEFAULT_GOD_MODE,
    val useCommonUIMode: Boolean = Configuration.DEFAULT_USE_COMMON_UI_MODE,
    @ConnectTimeout val connectTimeout: Int = Configuration.DEFAULT_CONNECT_TIMEOUT,
    val experimentalMode: Boolean = Configuration.DEFAULT_EXPERIMENTAL_MODE,
    @ClipMode val clipMode: Int = Configuration.DEFAULT_CLIP_MODE,
    val scrollMode: Boolean = Configuration.DEFAULT_SCROLL_MODE,
    val autoRefresh: Boolean = Configuration.DEFAULT_AUTO_REFRESH,
    val isSSLVerificationEnabled: Boolean = Configuration.DEFAULT_SSL_VERIFICATION,
    val fullInfoPlayer: Boolean = Configuration.DEFAULT_FULL_INFO_PLAYER,
    val initialTabTitle: Int = Configuration.DEFAULT_INITIAL_TAB_INDEX,
    val tabTitles: List<String> = emptyList(),
    val noPictureMode: Boolean = Configuration.DEFAULT_NO_PICTURE_MODE,
    val silentMode: Boolean = Configuration.DEFAULT_SILENT_MODE
)