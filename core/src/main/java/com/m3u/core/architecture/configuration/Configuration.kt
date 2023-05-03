package com.m3u.core.architecture.configuration

import com.m3u.core.annotation.ClipMode
import com.m3u.core.annotation.ConnectTimeout
import com.m3u.core.annotation.FeedStrategy

/**
 * This is a static key-value configuration standard.
 */
interface Configuration {
    @FeedStrategy
    var feedStrategy: Int
    var useCommonUIMode: Boolean
    var rowCount: Int

    @ConnectTimeout
    var connectTimeout: Int
    var godMode: Boolean
    var experimentalMode: Boolean

    @ClipMode
    var clipMode: Int
    var autoRefresh: Boolean
    var fullInfoPlayer: Boolean

    @ExperimentalConfiguration
    var scrollMode: Boolean

    @ExperimentalConfiguration
    var isSSLVerification: Boolean

    var initialTabIndex: Int

    var noPictureMode: Boolean

    var silentMode: Boolean

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
        const val DEFAULT_SILENT_MODE = false
    }
}
