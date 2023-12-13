package com.m3u.features.playlist

import com.m3u.core.wrapper.Message
import com.m3u.i18n.R.string

sealed class PlaylistMessage(
    resId: Int,
    vararg formatArgs: Any
) : Message(resId, formatArgs) {
    data object PlaylistUrlNotFound : PlaylistMessage(string.feat_playlist_error_playlist_url_not_found)

    data class PlaylistNotFound(val playlistUrl: String) :
        PlaylistMessage(string.feat_playlist_error_playlist_not_found, playlistUrl)

    data object StreamNotFound : PlaylistMessage(string.feat_playlist_error_stream_not_found)

    data object StreamCoverNotFound : PlaylistMessage(string.feat_playlist_error_stream_cover_not_found)

    data class StreamCoverSaved(val path: String) :
        PlaylistMessage(string.feat_playlist_success_save_cover, path)
}
