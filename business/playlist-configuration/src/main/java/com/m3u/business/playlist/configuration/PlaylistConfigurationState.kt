package com.m3u.business.playlist.configuration

import com.m3u.core.foundation.util.basic.PlaylistInputKind
import com.m3u.core.foundation.util.basic.normalizePlaylistInputForSubmission
import com.m3u.data.database.model.Playlist

sealed interface PlaylistConfigurationState {
    val playlistReference: String?

    data object Loading : PlaylistConfigurationState {
        override val playlistReference: String? = null
    }

    data class Content(
        val playlist: Playlist,
        override val playlistReference: String =
            playlistConfigurationReference(playlist.url),
    ) : PlaylistConfigurationState

    data class NotFound(
        override val playlistReference: String,
    ) : PlaylistConfigurationState
}

internal fun resolvePlaylistConfigurationState(
    playlists: List<Playlist>,
    playlistReference: String,
): PlaylistConfigurationState {
    val playlistUrl = resolvePlaylistConfigurationUrl(
        playlistUrls = playlists.map(Playlist::url),
        routeArgument = playlistReference,
    )
    val playlist = playlists.firstOrNull { candidate ->
        candidate.url == playlistUrl
    }
    return playlist
        ?.let(PlaylistConfigurationState::Content)
        ?: PlaylistConfigurationState.NotFound(
            playlistReference = playlistReference.safePlaylistConfigurationReference(),
        )
}

internal fun normalizePlaylistTitle(title: String): String? =
    title
        .normalizePlaylistInputForSubmission(PlaylistInputKind.TITLE)
        .takeIf(String::isNotEmpty)

internal fun normalizePlaylistUserAgent(userAgent: String?): String? =
    userAgent
        ?.normalizePlaylistInputForSubmission(PlaylistInputKind.USER_AGENT)
        ?.takeIf(String::isNotEmpty)

enum class PlaylistRemovalState {
    IDLE,
    REMOVING,
    FAILED,
    REMOVED,
}

private val PLAYLIST_CONFIGURATION_REFERENCE =
    Regex("playlist-work:[a-f0-9]{64}")

private fun String.safePlaylistConfigurationReference(): String =
    takeIf(PLAYLIST_CONFIGURATION_REFERENCE::matches)
        ?: playlistConfigurationReference(this)
