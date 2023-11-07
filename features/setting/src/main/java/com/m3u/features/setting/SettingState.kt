package com.m3u.features.setting

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.m3u.core.annotation.ClipMode
import com.m3u.core.annotation.ConnectTimeout
import com.m3u.core.annotation.FeedStrategy
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.architecture.configuration.ExperimentalConfiguration
import com.m3u.data.database.entity.Live

@OptIn(ExperimentalConfiguration::class)
data class SettingState(
    private val configuration: Configuration,
    val versionName: String = "",
    val versionCode: Int = -1,
    val title: String = "",
    val url: String = "",
    val uri: Uri = Uri.EMPTY,
    val mutedLives: List<Live> = emptyList(),
    val localStorage: Boolean = false,
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

    var scrollMode: Boolean by configuration.scrollMode
    var autoRefresh: Boolean by configuration.autoRefresh

    var isSSLVerification: Boolean by configuration.isSSLVerification
    var fullInfoPlayer: Boolean by configuration.fullInfoPlayer
    var initialRootDestination: Int by configuration.initialRootDestination
    var noPictureMode: Boolean by configuration.noPictureMode

    var cinemaMode: Boolean by configuration.cinemaMode


    val actualUrl
        get() = if (localStorage) {
            uri.takeIf { uri != Uri.EMPTY }?.toString()
        } else {
            url.ifEmpty { null }
        }
}