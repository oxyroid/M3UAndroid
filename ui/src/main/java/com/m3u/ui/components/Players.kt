package com.m3u.ui.components

import android.graphics.Rect
import android.util.Log
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.State
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.m3u.ui.model.Background
import com.m3u.ui.model.LocalBackground
import com.m3u.ui.util.LifecycleEffect

@Immutable
data class PlayerState(
    val url: String,
    val resizeMode: Int,
    val keepScreenOn: Boolean,
    val playbackState: MutableState<@State Int>,
    val playerRect: MutableState<Rect>,
    val exception: MutableState<PlaybackException?>
)

@Composable
fun rememberPlayerState(
    url: String,
    @OptIn(UnstableApi::class)
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    keepScreenOn: Boolean = true,
    state: MutableState<@State Int> = remember(url) { mutableStateOf(Player.STATE_IDLE) },
    rect: MutableState<Rect> = remember(url) { mutableStateOf(Rect()) },
    exception: MutableState<PlaybackException?> = remember(url) { mutableStateOf(null) }
): PlayerState = remember(url, resizeMode, keepScreenOn, state, rect, exception) {
    PlayerState(url, resizeMode, keepScreenOn, state, rect, exception)
}

@OptIn(UnstableApi::class)
@Composable
fun ExoPlayer(
    state: PlayerState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val (url, resizeMode, keepScreenOn, playerState, videoSize, exception) = state
    val mediaItem = remember(url) { MediaItem.fromUri(url) }
    val player = remember(mediaItem) {
        ExoPlayer.Builder(context)
            .build()
            .apply {
                val attributes = AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build()
                setAudioAttributes(attributes, true)
                playWhenReady = true
                setMediaItem(mediaItem)
            }
    }

    DisposableEffect(player, playerState, videoSize, exception) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                super.onVideoSizeChanged(size)
                Log.d("Player", "onVideoSizeChanged: ${size.pixelWidthHeightRatio}")
                videoSize.value = Rect(0, 0, size.width, size.height)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)
                playerState.value = playbackState
            }

            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                exception.value = error
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    CompositionLocalProvider(LocalBackground provides Background(Color.Black)) {
        Background(modifier) {
            var lifecycle: Lifecycle.Event by remember { mutableStateOf(Lifecycle.Event.ON_CREATE) }
            LifecycleEffect { lifecycle = it }
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false
                        setKeepScreenOn(keepScreenOn)
                        setResizeMode(resizeMode)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    view.apply {
                        setPlayer(player)
                    }
                    when (lifecycle) {
                        Lifecycle.Event.ON_RESUME -> {
                            view.player?.play()
                            view.onResume()
                        }

                        Lifecycle.Event.ON_STOP -> {
                            view.player?.pause()
                            view.onPause()
                        }

                        else -> {}
                    }
                }
            )
            DisposableEffect(player) {
                player.prepare()
                onDispose {
                    player.release()
                }
            }
        }
    }
}