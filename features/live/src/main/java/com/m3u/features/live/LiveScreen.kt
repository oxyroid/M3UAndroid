package com.m3u.features.live

import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player.*
import androidx.media3.common.Player.State
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.m3u.core.util.context.toast
import com.m3u.ui.components.*
import com.m3u.ui.model.LocalTheme
import com.m3u.ui.model.LocalUtils
import com.m3u.ui.util.EventHandler
import com.m3u.ui.util.LifecycleEffect

@Composable
internal fun LiveRoute(
    modifier: Modifier = Modifier,
    viewModel: LiveViewModel = hiltViewModel(),
    id: Int
) {
    val context = LocalContext.current
    val utils = LocalUtils.current
    val state: LiveState by viewModel.state.collectAsStateWithLifecycle()

    val systemUiController = rememberSystemUiController()
    val useDarkIcons = !isSystemInDarkTheme()

    LifecycleEffect { event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                utils.hideSystemUI()
                utils.setActions()
                systemUiController.setSystemBarsColor(
                    color = Color.Black,
                    darkIcons = false
                )
            }

            Lifecycle.Event.ON_PAUSE -> {
                utils.showSystemUI()
                utils.setActions()
                systemUiController.setSystemBarsColor(
                    color = Color.Transparent,
                    darkIcons = useDarkIcons
                )
            }

            else -> {}
        }
    }

    EventHandler(state.message) {
        context.toast(it)
    }

    LaunchedEffect(id) {
        viewModel.onEvent(LiveEvent.Init(id))
    }
    LiveScreen(
        modifier = modifier,
        url = state.live?.url,
        playerRect = playerRect
    )
}

@Composable
private fun LiveScreen(
    modifier: Modifier = Modifier,
    url: String?,
    playerRect: MutableState<Rect>
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("features:live")
    ) {
        val state = rememberPlayerState(
            url = url.orEmpty(),
            rect = playerRect
        )
        ExoPlayer(
            state = state,
            modifier = Modifier.fillMaxSize()
        )
        val maskState = rememberMaskState()

        CompositionLocalProvider(
            LocalContentColor provides Color.White
        ) {
            MaskPanel(
                state = maskState,
                horizontalAlignment = Alignment.End
            ) {
                val playback by state.playbackState
                Text(
                    text = playback.displayText,
                    fontWeight = FontWeight.Bold
                )
                val exception by state.exception
                val displayText = exception.displayText
                if (displayText.isNotEmpty()) {
                    Text(
                        text = displayText,
                        color = LocalTheme.current.error
                    )
                }
            }
            Mask(
                state = maskState,
                backgroundColor = Color.Black.copy(alpha = 0.54f),
                modifier = Modifier.fillMaxSize()
            ) {
                MaskCircleButton(
                    state = maskState,
                    icon = Icons.Rounded.Refresh,
                    onClick = { /*TODO*/ }
                )
            }
        }
    }
}

private val PlaybackException?.displayText: String
    @Composable get() = when (this) {
        null -> ""
        else -> "[$errorCode] $errorCodeName"
    }

private val @State Int.displayText: String
    @Composable get() = when (this) {
        STATE_IDLE -> R.string.playback_state_idle
        STATE_BUFFERING -> R.string.playback_state_buffering
        STATE_READY -> null
        STATE_ENDED -> R.string.playback_state_ended
        else -> null
    }
        ?.let { stringResource(id = it) }
        .orEmpty()
