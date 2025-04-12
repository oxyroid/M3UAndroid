package com.m3u.tv.screens.player.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.type
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun VideoPlayerControls(
    channel: Channel,
    playlist: Playlist,
    contentCurrentPosition: Long,
    contentDuration: Long,
    isPlaying: Boolean,
    isBuffering: Boolean,
    focusRequester: FocusRequester,
    onPlayPauseToggle: () -> Unit = {},
    onSeek: (Float) -> Unit = {},
    onShowControls: () -> Unit = {}
) {
    VideoPlayerMainFrame(
        mediaTitle = {
            VideoPlayerMediaTitle(
                title = channel.title,
                secondaryText = stringResource(playlist.source.resId),
                tertiaryText = playlist.title,
                type = when {
                    isBuffering -> VideoPlayerMediaTitleType.DEFAULT
                    contentDuration == C.TIME_UNSET -> VideoPlayerMediaTitleType.LIVE
                    else -> VideoPlayerMediaTitleType.DEFAULT
                }
            )
        },
        mediaActions = {
            Row(
                modifier = Modifier.padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VideoPlayerControlsIcon(
                    icon = Icons.Default.AutoAwesomeMotion,
                    isPlaying = isPlaying,
                    contentDescription = "VideoPlayerControlPlaylistButton",
                    onShowControls = onShowControls
                )
                VideoPlayerControlsIcon(
                    modifier = Modifier.padding(start = 12.dp),
                    icon = Icons.Default.ClosedCaption,
                    isPlaying = isPlaying,
                    contentDescription = "VideoPlayerControlClosedCaptionsButton",
                    onShowControls = onShowControls
                )
                VideoPlayerControlsIcon(
                    modifier = Modifier.padding(start = 12.dp),
                    icon = Icons.Default.Settings,
                    isPlaying = isPlaying,
                    contentDescription = "VideoPlayerControlSettingsButton",
                    onShowControls = onShowControls
                )
            }
        },
        seeker = {
            VideoPlayerSeeker(
                focusRequester = focusRequester,
                isPlaying = isPlaying,
                onPlayPauseToggle = onPlayPauseToggle,
                onSeek = onSeek,
                onShowControls = onShowControls,
                contentProgress = contentCurrentPosition.milliseconds,
                contentDuration = contentDuration.milliseconds,
            )
        },
        more = null
    )
}
