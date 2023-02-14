package com.m3u.core.architecture

import com.m3u.core.annotation.FeedStrategy

interface Configuration {
    @FeedStrategy
    var feedStrategy: Int
    var useCommonUIMode: Boolean
    var mutedUrls: List<String>
    var rowCount: Int
    var showMutedAsFeed: Boolean

    companion object {
        const val DEFAULT_FEED_STRATEGY = FeedStrategy.SKIP_FAVORITE
        const val DEFAULT_USE_COMMON_UI_MODE = false
        const val DEFAULT_MUTED_URLS = "[]"
        const val DEFAULT_ROW_COUNT = 1
        const val DEFAULT_SHOW_MUTED_AS_FEED = false
    }
}
