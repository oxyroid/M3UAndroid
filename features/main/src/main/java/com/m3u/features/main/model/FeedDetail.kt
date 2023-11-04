package com.m3u.features.main.model

import com.m3u.data.database.entity.Feed

data class FeedDetail(
    val feed: Feed,
    val count: Int
) {
    companion object {
        const val DEFAULT_COUNT = 0
    }
}

internal fun Feed.toDetail(
    count: Int = FeedDetail.DEFAULT_COUNT
): FeedDetail = FeedDetail(
    feed = this,
    count = count
)