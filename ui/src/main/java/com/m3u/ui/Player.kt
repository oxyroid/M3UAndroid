package com.m3u.ui

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.m3u.core.annotation.ClipMode
import com.m3u.material.ktx.ifUnspecified
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Immutable
data class PlayerState(
    val player: Player?,
    val url: String,
    @ClipMode val clipMode: Int,
    val keepScreenOn: Boolean,
    val onInstallMedia: (String) -> Unit,
    val onUninstallMedia: () -> Unit
)

@Composable
fun rememberPlayerState(
    player: Player?,
    url: String,
    onInstallMedia: (String) -> Unit,
    @ClipMode clipMode: Int = ClipMode.ADAPTIVE,
    keepScreenOn: Boolean = true,
    onUninstallMedia: () -> Unit,
): PlayerState {
    val currentOnInstallMedia by rememberUpdatedState(onInstallMedia)
    val currentOnUninstallMedia by rememberUpdatedState(onUninstallMedia)
    return remember(
        player,
        url,
        clipMode,
        keepScreenOn,
        currentOnInstallMedia,
        currentOnUninstallMedia
    ) {
        PlayerState(
            player,
            url,
            clipMode,
            keepScreenOn,
            currentOnInstallMedia,
            currentOnUninstallMedia
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun Player(
    state: PlayerState,
    modifier: Modifier = Modifier,
    startColor: Color = Color.Unspecified,
) {
    val player = state.player
    val url = state.url
    val keepScreenOn = state.keepScreenOn
    val clipMode = state.clipMode
    var actualColor by remember(startColor) { mutableStateOf(startColor) }
    val currentShutterColor by animateColorAsState(
        targetValue = actualColor.ifUnspecified { Color.Black },
        label = "player-shutter-color",
        animationSpec = tween(durationMillis = 800, delayMillis = 400)
    )
    LaunchedEffect(startColor) {
        actualColor = Color.Black
    }

    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setShutterBackgroundColor(currentShutterColor.toArgb())
            }
        },
        modifier = modifier
    ) { view ->
        view.apply {
            setShutterBackgroundColor(currentShutterColor.toArgb())
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
    val scope = rememberCoroutineScope()
    DisposableEffect(player, url) {
        scope.launch {
            delay(150.milliseconds)
            state.onInstallMedia(url)
        }
        onDispose {
            state.onUninstallMedia()
        }
    }
}
