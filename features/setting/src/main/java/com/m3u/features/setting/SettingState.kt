package com.m3u.features.setting

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.m3u.core.annotation.ClipMode
import com.m3u.core.annotation.ConnectTimeout
import com.m3u.core.annotation.FeedStrategy
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.architecture.configuration.ExperimentalConfiguration
import com.m3u.data.database.entity.Live

data class SettingState(
    private val configuration: Configuration,
    val version: String = "",
    val title: String = "",
    val url: String = "",
    val mutedLives: List<Live> = emptyList(),
    val destinations: List<String> = emptyList(),
) {
    @FeedStrategy
    var feedStrategy: Int by configuration.feedStrategy
    var godMode: Boolean by configuration.godMode
    var useCommonUIMode: Boolean by configuration.useCommonUIMode

    @ConnectTimeout
    var connectTimeout: Int by configuration.connectTimeout
    var experimentalMode: Boolean by configuration.experimentalMode

    @ClipMode
    var clipMode: Int by configuration.clipMode

    @ExperimentalConfiguration
    var scrollMode: Boolean by configuration.scrollMode
    var autoRefresh: Boolean by configuration.autoRefresh

    @ExperimentalConfiguration
    var isSSLVerification: Boolean by configuration.isSSLVerification
    var fullInfoPlayer: Boolean by configuration.fullInfoPlayer
    var defaultDestination: Int by configuration.initialTabIndex
    var noPictureMode: Boolean by configuration.noPictureMode

    @ExperimentalConfiguration
    var cinemaMode: Boolean by configuration.cinemaMode
}