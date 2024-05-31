package com.m3u.ui

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.m3u.core.architecture.preferences.ClipMode

@Immutable
data class PlayerState(
    val player: Player?,
    @ClipMode val clipMode: Int,
    val keepScreenOn: Boolean
)

@Composable
fun rememberPlayerState(
    player: Player?,
    @ClipMode clipMode: Int = ClipMode.ADAPTIVE,
    keepScreenOn: Boolean = true,
): PlayerState {
    return remember(
        player,
        clipMode,
        keepScreenOn
    ) {
        PlayerState(
            player,
            clipMode,
            keepScreenOn
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun Player(
    state: PlayerState,
    modifier: Modifier = Modifier
) {
    val player = state.player
    val keepScreenOn = state.keepScreenOn
    val clipMode = state.clipMode

    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                useController = false
            }
        },
        update = { view ->
            view.apply {
                this.player = player
                setKeepScreenOn(keepScreenOn)
                resizeMode = when (clipMode) {
                    ClipMode.ADAPTIVE -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    ClipMode.CLIP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    ClipMode.STRETCHED -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            }
        },
        onRelease = {
            it.player = null
            it.keepScreenOn = false
        }
    )
}
