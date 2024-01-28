package com.m3u.features.playlist

import com.m3u.core.wrapper.Message
import com.m3u.i18n.R.string
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

sealed class PlaylistMessage(
    override val level: Int,
    override val type: Int,
    override val duration: Duration = 3.seconds,
    resId: Int,
    vararg formatArgs: Any
) : Message.Static(level, "playlist", type, duration, resId, formatArgs) {

    data object StreamNotFound : PlaylistMessage(
        level = LEVEL_ERROR,
        type = TYPE_SNACK,
        resId = string.feat_playlist_error_stream_not_found
    )

    data object StreamCoverNotFound : PlaylistMessage(
        level = LEVEL_ERROR,
        type = TYPE_SNACK,
        resId = string.feat_playlist_error_stream_cover_not_found
    )

    data class StreamCoverSaved(val path: String) : PlaylistMessage(
        level = LEVEL_INFO,
        type = TYPE_SNACK,
        resId = string.feat_playlist_success_save_cover,
        formatArgs = arrayOf(path)
    )

    data object Refreshing : PlaylistMessage(
        level = LEVEL_INFO,
        type = TYPE_SNACK,
        duration = Duration.INFINITE,
        resId = string.feat_playlist_refreshing
    )
}
