package com.m3u.features.feed

import com.m3u.core.wrapper.Message
import com.m3u.i18n.R.string

sealed class FeedMessage(
    resId: Int,
    vararg formatArgs: Any
) : Message(resId, formatArgs) {
    data object FeedUrlNotFound : FeedMessage(string.feat_feed_error_feed_url_not_found)

    data class FeedNotFound(val feedUrl: String) :
        FeedMessage(string.feat_feed_error_feed_not_found, feedUrl)

    data object LiveNotFound : FeedMessage(string.feat_feed_error_live_not_found)

    data object LiveCoverNotFound : FeedMessage(string.feat_feed_error_live_cover_not_found)

    data class LiveCoverSaved(val path: String) :
        FeedMessage(string.feat_feed_success_save_cover, path)
}
