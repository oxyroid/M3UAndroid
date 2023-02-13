package com.m3u.core.architecture

import com.m3u.core.annotation.SyncMode

interface Configuration {
    @SyncMode
    var syncMode: Int
    var useCommonUIMode: Boolean
    var mutedUrls: List<String>
}
