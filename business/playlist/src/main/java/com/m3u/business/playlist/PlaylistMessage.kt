package com.m3u.business.playlist

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

    data object ChannelNotFound : PlaylistMessage(
        level = LEVEL_ERROR,
        type = TYPE_SNACK,
        resId = string.feat_playlist_error_channel_not_found
    )

    data object ChannelCoverNotFound : PlaylistMessage(
        level = LEVEL_ERROR,
        type = TYPE_SNACK,
        resId = string.feat_playlist_error_channel_cover_not_found
    )

    data class ChannelCoverSaved(val path: String) : PlaylistMessage(
        level = LEVEL_INFO,
        type = TYPE_SNACK,
        resId = string.feat_playlist_success_save_cover,
        formatArgs = arrayOf(path)
    )

    data object WebServerStarted : PlaylistMessage(
        level = LEVEL_INFO,
        type = TYPE_SNACK,
        resId = string.feat_playlist_web_server_started
    )

    data object WebServerStopped : PlaylistMessage(
        level = LEVEL_INFO,
        type = TYPE_SNACK,
        resId = string.feat_playlist_web_server_stopped
    )

    data class WebServerError(val error: String) : PlaylistMessage(
        level = LEVEL_ERROR,
        type = TYPE_SNACK,
        resId = string.feat_playlist_web_server_error,
        formatArgs = arrayOf(error)
    )
}
