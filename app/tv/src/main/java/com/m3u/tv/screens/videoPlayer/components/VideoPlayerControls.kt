package com.m3u.tv.screens.videoPlayer.components

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
import androidx.compose.ui.unit.dp
import com.m3u.data.database.model.Channel
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun VideoPlayerControls(
    channel: Channel,
    contentCurrentPosition: Long,
    contentDuration: Long,
    isPlaying: Boolean,
    focusRequester: FocusRequester,
    onPlayPauseToggle: () -> Unit = {},
    onSeek: (Float) -> Unit = {},
    onShowControls: () -> Unit = {}
) {
    VideoPlayerMainFrame(
        mediaTitle = {
            VideoPlayerMediaTitle(
                title = channel.title,
                secondaryText = "channel.releaseDate",
                tertiaryText = "channel.director",
                type = VideoPlayerMediaTitleType.DEFAULT
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
