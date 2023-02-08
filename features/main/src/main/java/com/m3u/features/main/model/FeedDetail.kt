package com.m3u.features.main.model

import com.m3u.data.entity.Feed

data class FeedDetail(
    val feed: Feed,
    val count: Int
)

internal fun Feed.toDetail(
    count: Int = 0
): FeedDetail {
    return FeedDetail(
        this, count
    )
}