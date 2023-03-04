package com.m3u.ui.components

import android.content.Context
import android.graphics.Rect
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.m3u.core.annotation.ClipMode
import com.m3u.ui.model.Background
import com.m3u.ui.model.LocalBackground

@Immutable
@OptIn(UnstableApi::class)
data class PlayerState(
    val url: String,
    @ClipMode val clipMode: Int,
    val keepScreenOn: Boolean,
    val context: Context,
    val playbackState: MutableState<@Player.State Int>,
    val playerRect: MutableState<Rect>,
    val exception: MutableState<PlaybackException?>
) {
    private val mediaItem = MediaItem.fromUri(url)
    private val trackSelector = DefaultTrackSelector(context).apply {
        setParameters(buildUponParameters().setMaxVideoSizeSd())
    }
    var player: Player = ExoPlayer.Builder(context)
        .setTrackSelector(trackSelector)
        .build()
        .apply {
            val attributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            setAudioAttributes(attributes, true)
            playWhenReady = true
        }
        private set

    fun loadMedia() {
        player.setMediaItem(mediaItem)
    }
}

@Composable
fun rememberPlayerState(
    url: String,
    @ClipMode clipMode: Int = ClipMode.ADAPTIVE,
    keepScreenOn: Boolean = true,
    context: Context = LocalContext.current,
    state: MutableState<@Player.State Int> = remember(url) { mutableStateOf(Player.STATE_IDLE) },
    videoSize: MutableState<Rect> = remember(url) { mutableStateOf(Rect()) },
    exception: MutableState<PlaybackException?> = remember(url) { mutableStateOf(null) }
): PlayerState = remember(url, clipMode, keepScreenOn, context, state, videoSize, exception) {
    PlayerState(url, clipMode, keepScreenOn, context, state, videoSize, exception)
}

@OptIn(UnstableApi::class)
@Composable
fun ExoPlayer(
    state: PlayerState,
    modifier: Modifier = Modifier
) {
    val player = state.player
    val keepScreenOn = state.keepScreenOn
    val playerState = state.playbackState
    val videoSize = state.playerRect
    val exception = state.exception
    val clipMode = state.clipMode

    DisposableEffect(player, playerState, videoSize, exception) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                super.onVideoSizeChanged(size)
                videoSize.value = Rect(0, 0, size.width, size.height)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)
                playerState.value = playbackState
            }

            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                when (error.errorCode) {
                    PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                        state.player.seekToDefaultPosition()
                        state.player.prepare()
                    }
                    else -> {}
                }
                exception.value = error
            }
        }
        state.player.addListener(listener)
        onDispose {
            state.player.removeListener(listener)
        }
    }

    CompositionLocalProvider(LocalBackground provides Background(Color.Black)) {
        Background(modifier) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                }
            ) { view ->
                view.apply {
                    setPlayer(player)
                    setKeepScreenOn(keepScreenOn)
                    resizeMode = when (clipMode) {
                        ClipMode.ADAPTIVE -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        ClipMode.CLIP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        ClipMode.STRETCHED -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                }
            }
            DisposableEffect(state.player) {
                state.loadMedia()
                state.player.prepare()
                onDispose {
                    state.player.release()
                }
            }
        }
    }
}