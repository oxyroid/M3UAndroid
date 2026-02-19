package com.m3u.tv.screens.player.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
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
    onShowControls: () -> Unit = {},
    onFavourite: () -> Unit = {},
    onEnterPip: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onClosedCaptionsClick: () -> Unit = {},
) {
    VideoPlayerMainFrame(
        mediaTitle = {
            VideoPlayerMediaTitle(
                title = channel.title,
                secondaryText = "",
                // secondaryText = "channel.releaseDate",
                tertiaryText = "",
                // tertiaryText = "channel.director",
                type = VideoPlayerMediaTitleType.DEFAULT
            )
        },
        mediaActions = {
            Row(
                modifier = Modifier.padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // VideoPlayerControlsIcon(
                //     icon = Icons.Default.AutoAwesomeMotion,
                //     isPlaying = isPlaying,
                //     contentDescription = "VideoPlayerControlPlaylistButton",
                //     onShowControls = onShowControls
                // )
                VideoPlayerControlsIcon(
                    modifier = Modifier.padding(start = 12.dp),
                    icon = if (channel.favourite) Icons.Filled.Star else Icons.Outlined.Star,
                    isPlaying = isPlaying,
                    contentDescription = "VideoPlayerControlFavouriteButton",
                    onShowControls = onShowControls,
                    onClick = onFavourite,
                    tint = if (channel.favourite) Color(0xffffcd3c) else null
                )
                VideoPlayerControlsIcon(
                    modifier = Modifier.padding(start = 12.dp),
                    icon = Icons.Default.ClosedCaption,
                    isPlaying = isPlaying,
                    contentDescription = "VideoPlayerControlClosedCaptionsButton",
                    onShowControls = onShowControls,
                    onClick = onClosedCaptionsClick
                )
                VideoPlayerControlsIcon(
                    modifier = Modifier.padding(start = 12.dp),
                    icon = Icons.Default.PictureInPicture,
                    isPlaying = isPlaying,
                    contentDescription = "VideoPlayerControlPipButton",
                    onShowControls = onShowControls,
                    onClick = onEnterPip
                )
                VideoPlayerControlsIcon(
                    modifier = Modifier.padding(start = 12.dp),
                    icon = Icons.Default.Settings,
                    isPlaying = isPlaying,
                    contentDescription = "VideoPlayerControlSettingsButton",
                    onShowControls = onShowControls,
                    onClick = onSettingsClick
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
