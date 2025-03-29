package com.m3u.tv.screens.player.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlin.time.Duration

@Composable
fun VideoPlayerSeeker(
    focusRequester: FocusRequester,
    isPlaying: Boolean,
    contentProgress: Duration,
    contentDuration: Duration,
    onPlayPauseToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    onShowControls: () -> Unit = {},
) {
    val contentProgressString =
        contentProgress.toComponents { h, m, s, _ ->
            if (h > 0) {
                "$h:${m.padStartWith0()}:${s.padStartWith0()}"
            } else {
                "${m.padStartWith0()}:${s.padStartWith0()}"
            }
        }
    val contentDurationString =
        contentDuration.toComponents { h, m, s, _ ->
            if (h > 0) {
                "$h:${m.padStartWith0()}:${s.padStartWith0()}"
            } else {
                "${m.padStartWith0()}:${s.padStartWith0()}"
            }
        }

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        VideoPlayerControlsIcon(
            modifier = Modifier.focusRequester(focusRequester),
            icon = if (!isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause,
            onClick = onPlayPauseToggle,
            isPlaying = isPlaying,
            contentDescription = "VideoPlayerControlPlayPauseButton"
        )
        VideoPlayerControllerText(text = contentProgressString)
        VideoPlayerControllerIndicator(
            progress = (contentProgress / contentDuration).toFloat(),
            onSeek = onSeek,
            onShowControls = onShowControls
        )
        VideoPlayerControllerText(text = contentDurationString)
    }
}

private fun Number.padStartWith0() = this.toString().padStart(2, '0')
