package com.m3u.tv.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.media3.common.PlaybackException
import com.m3u.data.database.model.AdjacentChannels
import com.m3u.data.database.model.Channel
import com.m3u.tv.screens.player.components.VideoPlayerControls
import com.m3u.tv.screens.player.components.VideoPlayerOverlay
import com.m3u.tv.screens.player.components.VideoPlayerTrackSelectionDialog
import com.m3u.tv.screens.player.components.VideoPlayerPulse
import com.m3u.tv.screens.player.components.VideoPlayerPulse.Type.BACK
import com.m3u.tv.screens.player.components.VideoPlayerPulse.Type.FORWARD
import com.m3u.tv.screens.player.components.VideoPlayerPulseState
import com.m3u.tv.screens.player.components.VideoPlayerState
import com.m3u.tv.screens.player.components.rememberVideoPlayerPulseState
import com.m3u.tv.screens.player.components.rememberVideoPlayerState
import com.m3u.tv.utils.LocalHelper
import com.m3u.tv.utils.handleDPadKeyEvents
import kotlinx.coroutines.delay
import com.m3u.core.architecture.preferences.hiltPreferences
import androidx.compose.runtime.DisposableEffect

object VideoPlayerScreen {
    const val ChannelIdBundleKey = "channelId"
}

@Composable
fun PlayerScreen(
    onBackPressed: () -> Unit,
    viewModel: ChannelViewModel = hiltViewModel()
) {
    val helper = LocalHelper.current
    val channel by viewModel.channel.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val enterPipMode by viewModel.enterPipMode.collectAsStateWithLifecycle()

    LaunchedEffect(enterPipMode) {
        if (enterPipMode) {
            val videoSize = playerState.videoSize
            helper.enterPipMode(
                if (!videoSize.isEmpty()) videoSize
                else android.graphics.Rect(0, 0, 16, 9)
            )
            viewModel.onPipEntered()
        }
    }

    val adjacentChannels by viewModel.adjacentChannels.collectAsStateWithLifecycle()

    val isPipSupported = helper.isPipSupported()

    when (val channel = channel) {
        null -> {}
        else -> {
            VideoPlayerScreenContent(
                channel = channel,
                playerState = playerState,
                adjacentChannels = adjacentChannels,
                isPipSupported = isPipSupported,
                viewModel = viewModel,
                onBackPressed = onBackPressed,
                onFavourite = viewModel::onFavourite,
                onEnterPip = viewModel::enterPip,
            )
        }
    }
}

@Composable
fun VideoPlayerScreenContent(
    channel: Channel,
    playerState: PlayerState,
    adjacentChannels: AdjacentChannels?,
    isPipSupported: Boolean,
    viewModel: ChannelViewModel,
    onBackPressed: () -> Unit,
    onFavourite: () -> Unit,
    onEnterPip: () -> Unit,
) {
    val helper = LocalHelper.current
    val preferences = hiltPreferences()
    val player = playerState.player
    var showTrackSelection by remember { mutableStateOf(false) }
    var showSubtitlesModal by remember { mutableStateOf(false) }
    val tracks by viewModel.tracks.collectAsStateWithLifecycle(emptyMap())
    val selectedFormats by viewModel.currentTracks.collectAsStateWithLifecycle(emptyMap())

    LaunchedEffect(playerState.videoSize) {
        helper.latestVideoSize = if (!playerState.videoSize.isEmpty()) playerState.videoSize
        else android.graphics.Rect(0, 0, 16, 9)
    }
    DisposableEffect(Unit) {
        helper.onUserLeaveHint = {
            if (preferences.pipOnHome && helper.isPipSupported()) {
                helper.enterPipMode(helper.latestVideoSize)
                viewModel.onPipEntered()
            }
        }
        onDispose {
            helper.onUserLeaveHint = null
        }
    }

    DisposableEffect(Unit) {
        helper.setKeepScreenOn(true)
        onDispose {
            helper.setKeepScreenOn(false)
        }
    }

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

        BackHandler(onBack = {
            if (preferences.backEntersPip && helper.isPipSupported()) {
                viewModel.enterPip()
            } else {
                onBackPressed()
            }
        })

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
                        onPlayPauseToggle = videoPlayerState::togglePlayPause,
                        onFavourite = onFavourite,
                        onEnterPip = onEnterPip,
                        onSettingsClick = { showSubtitlesModal = false; showTrackSelection = true },
                        onClosedCaptionsClick = { showTrackSelection = false; showSubtitlesModal = true },
                        hasPreviousChannel = adjacentChannels?.prevId != null,
                        hasNextChannel = adjacentChannels?.nextId != null,
                        onPreviousChannel = viewModel::getPreviousChannel,
                        onNextChannel = viewModel::getNextChannel,
                        isPipSupported = isPipSupported,
                        streamErrorMessage = formatPlaybackError(playerState.playerError),
                    )
                }
            )
        }

        VideoPlayerTrackSelectionDialog(
            visible = showTrackSelection || showSubtitlesModal,
            tracks = tracks,
            selectedFormats = selectedFormats,
            onDismiss = { showTrackSelection = false; showSubtitlesModal = false },
            onChooseTrack = { type, format -> viewModel.chooseTrack(type, format) },
            onClearTrack = { type -> viewModel.clearTrack(type) },
            subtitleOnly = showSubtitlesModal,
        )
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

private fun formatPlaybackError(exception: PlaybackException?): String {
    if (exception == null) return ""
    val codeName = PlaybackException.getErrorCodeName(exception.errorCode)
    val msg = exception.message?.takeIf { it.isNotBlank() }
    return if (msg != null) "$codeName: $msg" else codeName
}
