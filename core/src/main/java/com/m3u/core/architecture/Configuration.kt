package com.m3u.core.architecture

import com.m3u.core.annotation.FeedStrategy

interface Configuration {
    @FeedStrategy
    var feedStrategy: Int
    var useCommonUIMode: Boolean
    var mutedUrls: List<String>
}
