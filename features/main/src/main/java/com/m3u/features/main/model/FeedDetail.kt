package com.m3u.features.main.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import com.m3u.data.database.entity.Feed

@Immutable
internal data class FeedDetail(
    val feed: Feed,
    val count: Int
) {
    companion object {
        const val DEFAULT_COUNT = 0
    }
}

@Immutable
internal data class FeedDetailHolder(
    val details: List<FeedDetail> = emptyList()
)

@Composable
internal fun rememberFeedDetailHolder(details: List<FeedDetail>): FeedDetailHolder {
    return remember(details) {
        FeedDetailHolder(details)
    }
}

internal fun Feed.toDetail(
    count: Int = FeedDetail.DEFAULT_COUNT
): FeedDetail = FeedDetail(
    feed = this,
    count = count
)