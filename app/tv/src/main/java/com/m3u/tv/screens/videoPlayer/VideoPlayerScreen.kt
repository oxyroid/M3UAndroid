package com.m3u.tv.screens.videoPlayer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import com.m3u.business.channel.ChannelViewModel
import com.m3u.business.channel.PlayerState
import com.m3u.core.foundation.ui.thenNoN
import com.m3u.data.database.model.Channel
import com.m3u.tv.screens.videoPlayer.components.VideoPlayerControls
import com.m3u.tv.screens.videoPlayer.components.VideoPlayerOverlay
import com.m3u.tv.screens.videoPlayer.components.VideoPlayerPulse
import com.m3u.tv.screens.videoPlayer.components.VideoPlayerPulse.Type.BACK
import com.m3u.tv.screens.videoPlayer.components.VideoPlayerPulse.Type.FORWARD
import com.m3u.tv.screens.videoPlayer.components.VideoPlayerPulseState
import com.m3u.tv.screens.videoPlayer.components.VideoPlayerState
import com.m3u.tv.screens.videoPlayer.components.rememberVideoPlayerPulseState
import com.m3u.tv.screens.videoPlayer.components.rememberVideoPlayerState
import com.m3u.tv.utils.handleDPadKeyEvents
import kotlinx.coroutines.delay

object VideoPlayerScreen {
    const val ChannelIdBundleKey = "channelId"
}

@Composable
fun VideoPlayerScreen(
    onBackPressed: () -> Unit,
    viewModel: ChannelViewModel = hiltViewModel()
) {
    val channel by viewModel.channel.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    when (val channel = channel) {
        null -> {} // do nothing
        else -> {
            VideoPlayerScreenContent(
                channel = channel,
                playerState = playerState,
                onBackPressed = onBackPressed
            )
        }
    }
}

@Composable
fun VideoPlayerScreenContent(
    channel: Channel,
    playerState: PlayerState,
    onBackPressed: () -> Unit
) {
    val player = playerState.player
    if (player != null) {
        val videoPlayerState = rememberVideoPlayerState(
            player = player,
            hideSeconds = 4,
        )

        var contentCurrentPosition by remember { mutableLongStateOf(0L) }

        LaunchedEffect(Unit) {
            while (true) {
                delay(300)
                contentCurrentPosition = player.currentPosition
            }
        }

        BackHandler(onBack = onBackPressed)

        val pulseState = rememberVideoPlayerPulseState()

        Box(
            Modifier
                .thenNoN(player) { player ->
                    Modifier.dPadEvents(
                        player,
                        videoPlayerState,
                        pulseState
                    )
                }
                .focusable()
        ) {
            PlayerSurface(
                player = player,
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
                        contentDuration = player.duration,
                        isPlaying = videoPlayerState.isPlaying,
                        focusRequester = focusRequester,
                        onShowControls = videoPlayerState::showControls,
                        onSeek = { player.seekTo(player.duration.times(it).toLong()) },
                        onPlayPauseToggle = videoPlayerState::togglePlayPause
                    )
                }
            )
        }
    }

}

private fun Modifier.dPadEvents(
    player: Player,
    videoPlayerState: VideoPlayerState,
    pulseState: VideoPlayerPulseState
): Modifier = this.handleDPadKeyEvents(
    onLeft = {
        if (!videoPlayerState.isControlsVisible) {
            player.seekBack()
            pulseState.setType(BACK)
        }
    },
    onRight = {
        if (!videoPlayerState.isControlsVisible) {
            player.seekForward()
            pulseState.setType(FORWARD)
        }
    },
    onUp = { videoPlayerState.showControls() },
    onDown = { videoPlayerState.showControls() },
    onEnter = {
        player.pause()
        videoPlayerState.showControls()
    }
)
