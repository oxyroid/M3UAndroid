package com.m3u.core.architecture

import com.m3u.core.annotation.ClipMode
import com.m3u.core.annotation.ConnectTimeout
import com.m3u.core.annotation.FeedStrategy

interface Configuration {
    @FeedStrategy
    var feedStrategy: Int
    var useCommonUIMode: Boolean
    var rowCount: Int

    @ConnectTimeout
    var connectTimeout: Int
    var editMode: Boolean
    var experimentalMode: Boolean

    @ClipMode
    var clipMode: Int
    var scrollMode: Boolean

    companion object {
        @FeedStrategy
        const val DEFAULT_FEED_STRATEGY = FeedStrategy.SKIP_FAVORITE
        const val DEFAULT_USE_COMMON_UI_MODE = false
        const val DEFAULT_ROW_COUNT = 1

        @ConnectTimeout
        const val DEFAULT_CONNECT_TIMEOUT = ConnectTimeout.SHORT
        const val DEFAULT_EDIT_MODE = false
        const val DEFAULT_EXPERIMENTAL_MODE = false

        @ClipMode
        const val DEFAULT_CLIP_MODE = ClipMode.ADAPTIVE
        const val DEFAULT_SCROLL_MODE = false
    }
}
