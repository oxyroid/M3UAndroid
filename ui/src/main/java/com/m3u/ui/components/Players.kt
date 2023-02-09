package com.m3u.ui.components

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.m3u.ui.util.LifecycleEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

@Immutable
data class PlayerState(
    val url: String,
    val keepScreenOn: Boolean,
    internal val playbackStateSource: MutableStateFlow<@State Int>,
    internal val exceptionSource: MutableStateFlow<PlaybackException?> = MutableStateFlow(null)
) {
    val playbackState: Flow<@State Int> get() = playbackStateSource
    val exception: Flow<PlaybackException?> get() = exceptionSource
}

@Composable
fun rememberPlayerState(
    url: String,
    keepScreenOn: Boolean = true,
    state: MutableStateFlow<@State Int> = remember(url) { MutableStateFlow(Player.STATE_IDLE) },
    exception: MutableStateFlow<PlaybackException?> = remember(url) { MutableStateFlow(null) }
): PlayerState = remember(url, keepScreenOn, state, exception) {
    PlayerState(url, keepScreenOn, state, exception)
}

@Composable
@OptIn(UnstableApi::class)
fun ExoPlayer(
    state: PlayerState,
    modifier: Modifier = Modifier,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT
) {
    val context = LocalContext.current
    val (url, keepScreenOn, playerState, exception) = state
    val mediaItem = remember(url) {
        MediaItem.fromUri(url)
    }
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

    DisposableEffect(player, playerState, exception) {
        val listener = object : Player.Listener {
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

    PlayerBackground(modifier) {
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

@Composable
private fun PlayerBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        color = Color.Black,
        contentColor = Color.White,
        modifier = modifier,
        content = content
    )
}