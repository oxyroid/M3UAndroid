package com.m3u.tv.screens.videoPlayer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import com.m3u.data.database.model.Channel
import com.m3u.tv.common.Error
import com.m3u.tv.common.Loading
import com.m3u.tv.screens.videoPlayer.components.VideoPlayerControls
import com.m3u.tv.screens.videoPlayer.components.VideoPlayerOverlay
import com.m3u.tv.screens.videoPlayer.components.VideoPlayerPulse
import com.m3u.tv.screens.videoPlayer.components.VideoPlayerPulse.Type.BACK
import com.m3u.tv.screens.videoPlayer.components.VideoPlayerPulse.Type.FORWARD
import com.m3u.tv.screens.videoPlayer.components.VideoPlayerPulseState
import com.m3u.tv.screens.videoPlayer.components.VideoPlayerState
import com.m3u.tv.screens.videoPlayer.components.rememberPlayer
import com.m3u.tv.screens.videoPlayer.components.rememberVideoPlayerPulseState
import com.m3u.tv.screens.videoPlayer.components.rememberVideoPlayerState
import com.m3u.tv.utils.handleDPadKeyEvents
import kotlinx.coroutines.delay

object VideoPlayerScreen {
    const val ChannelIdBundleKey = "channelId"
}

/**
 * [Work in progress] A composable screen for playing a video.
 *
 * @param onBackPressed The callback to invoke when the user presses the back button.
 * @param videoPlayerScreenViewModel The view model for the video player screen.
 */
@Composable
fun VideoPlayerScreen(
    onBackPressed: () -> Unit,
    videoPlayerScreenViewModel: VideoPlayerScreenViewModel = hiltViewModel()
) {
    val uiState by videoPlayerScreenViewModel.uiState.collectAsStateWithLifecycle()

    // TODO: Handle Loading & Error states
    when (val s = uiState) {
        is VideoPlayerScreenUiState.Loading -> {
            Loading(modifier = Modifier.fillMaxSize())
        }

        is VideoPlayerScreenUiState.Error -> {
            Error(modifier = Modifier.fillMaxSize())
        }

        is VideoPlayerScreenUiState.Done -> {
            VideoPlayerScreenContent(
                channel = s.channel,
                onBackPressed = onBackPressed
            )
        }
        null -> {}
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreenContent(channel: Channel, onBackPressed: () -> Unit) {
    val exoPlayer = rememberPlayer(LocalContext.current)

    val videoPlayerState = rememberVideoPlayerState(
        exoPlayer = exoPlayer,
        hideSeconds = 4,
    )

    LaunchedEffect(exoPlayer, channel) {
        exoPlayer.setMediaItem(
            MediaItem.Builder()
                .setUri(channel.url)
                .build()
        )
        exoPlayer.prepare()
    }

    var contentCurrentPosition by remember { mutableLongStateOf(0L) }

    // TODO: Update in a more thoughtful manner
    LaunchedEffect(Unit) {
        while (true) {
            delay(300)
            contentCurrentPosition = exoPlayer.currentPosition
        }
    }

    BackHandler(onBack = onBackPressed)

    val pulseState = rememberVideoPlayerPulseState()

    Box(
        Modifier
            .dPadEvents(
                exoPlayer,
                videoPlayerState,
                pulseState
            )
            .focusable()
    ) {
        PlayerSurface(
            player = exoPlayer,
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            modifier = Modifier.resizeWithContentScale(
                contentScale = ContentScale.Fit,
                sourceSizeDp = null
            )
        )

        val focusRequester = remember { FocusRequester() }
        VideoPlayerOverlay(
            modifier = Modifier.align(Alignment.BottomCenter),
            focusRequester = focusRequester,
            isPlaying = videoPlayerState.isPlaying,
            isControlsVisible = videoPlayerState.isControlsVisible,
            centerButton = { VideoPlayerPulse(pulseState) },
            subtitles = { /* TODO Implement subtitles */ },
            showControls = videoPlayerState::showControls,
            controls = {
                VideoPlayerControls(
                    channel = channel,
                    contentCurrentPosition = contentCurrentPosition,
                    contentDuration = exoPlayer.duration,
                    isPlaying = videoPlayerState.isPlaying,
                    focusRequester = focusRequester,
                    onShowControls = videoPlayerState::showControls,
                    onSeek = { exoPlayer.seekTo(exoPlayer.duration.times(it).toLong()) },
                    onPlayPauseToggle = videoPlayerState::togglePlayPause
                )
            }
        )
    }
}

private fun Modifier.dPadEvents(
    exoPlayer: ExoPlayer,
    videoPlayerState: VideoPlayerState,
    pulseState: VideoPlayerPulseState
): Modifier = this.handleDPadKeyEvents(
    onLeft = {
        if (!videoPlayerState.isControlsVisible) {
            exoPlayer.seekBack()
            pulseState.setType(BACK)
        }
    },
    onRight = {
        if (!videoPlayerState.isControlsVisible) {
            exoPlayer.seekForward()
            pulseState.setType(FORWARD)
        }
    },
    onUp = { videoPlayerState.showControls() },
    onDown = { videoPlayerState.showControls() },
    onEnter = {
        exoPlayer.pause()
        videoPlayerState.showControls()
    }
)
