package com.m3u.core.architecture.configuration

import androidx.compose.runtime.MutableState
import com.m3u.core.annotation.ClipMode
import com.m3u.core.annotation.ConnectTimeout
import com.m3u.core.annotation.FeedStrategy

/**
 * This is a static key-value configuration standard.
 */
interface Configuration {
    @FeedStrategy
    val feedStrategy: MutableState<Int>
    val useCommonUIMode: MutableState<Boolean>
    val rowCount: MutableState<Int>

    @ConnectTimeout
    val connectTimeout: MutableState<Int>
    val godMode: MutableState<Boolean>
    val experimentalMode: MutableState<Boolean>

    @ClipMode
    val clipMode: MutableState<Int>
    val autoRefresh: MutableState<Boolean>
    val fullInfoPlayer: MutableState<Boolean>

    @ExperimentalConfiguration
    val scrollMode: MutableState<Boolean>

    @ExperimentalConfiguration
    val isSSLVerification: MutableState<Boolean>
    val initialTabIndex: MutableState<Int>
    val noPictureMode: MutableState<Boolean>
    val silentMode: MutableState<Boolean>

    @ExperimentalConfiguration
    val cinemaMode: MutableState<Boolean>

    companion object {
        @FeedStrategy
        const val DEFAULT_FEED_STRATEGY = FeedStrategy.SKIP_FAVORITE
        const val DEFAULT_USE_COMMON_UI_MODE = false
        const val DEFAULT_ROW_COUNT = 1

        @ConnectTimeout
        const val DEFAULT_CONNECT_TIMEOUT = ConnectTimeout.SHORT
        const val DEFAULT_GOD_MODE = false
        const val DEFAULT_EXPERIMENTAL_MODE = false

        @ClipMode
        const val DEFAULT_CLIP_MODE = ClipMode.ADAPTIVE
        const val DEFAULT_SCROLL_MODE = false
        const val DEFAULT_AUTO_REFRESH = false
        const val DEFAULT_SSL_VERIFICATION = true
        const val DEFAULT_FULL_INFO_PLAYER = false
        const val DEFAULT_INITIAL_TAB_INDEX = 0
        const val DEFAULT_NO_PICTURE_MODE = false
        const val DEFAULT_SILENT_MODE = true
        const val DEFAULT_CINEMA_MODE = false
    }
}
