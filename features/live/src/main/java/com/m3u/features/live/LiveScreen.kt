package com.m3u.features.live

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player.STATE_BUFFERING
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Player.STATE_READY
import androidx.media3.common.Player.State
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.m3u.core.util.context.toast
import com.m3u.ui.components.LivePlayer
import com.m3u.ui.components.M3UColumn
import com.m3u.ui.components.rememberPlayerState
import com.m3u.ui.model.AppAction
import com.m3u.ui.model.LocalTheme
import com.m3u.ui.model.SetActions
import com.m3u.ui.util.EventHandler
import com.m3u.ui.util.LifecycleEffect

@Composable
internal fun LiveRoute(
    modifier: Modifier = Modifier,
    viewModel: LiveViewModel = hiltViewModel(),
    setAppActions: SetActions,
    id: Int
) {
    val context = LocalContext.current
    val state: LiveState by viewModel.state.collectAsStateWithLifecycle()

    val setAppActionsUpdated by rememberUpdatedState(setAppActions)
    val systemUiController = rememberSystemUiController()
    val useDarkIcons = !isSystemInDarkTheme()

    LifecycleEffect { event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                val actions = listOf<AppAction>()
                setAppActionsUpdated(actions)
                systemUiController.setSystemBarsColor(
                    color = Color.Black,
                    darkIcons = false
                )
            }

            Lifecycle.Event.ON_PAUSE -> {
                setAppActionsUpdated(emptyList())
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
        url = state.live?.url
    )
}

@Composable
private fun LiveScreen(
    modifier: Modifier = Modifier,
    url: String?
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("features:live")
    ) {
        val state = rememberPlayerState(url.orEmpty())
        LivePlayer(
            state = state,
            modifier = Modifier.fillMaxSize()
        )
        M3UColumn(
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            val playback by state.playbackState.collectAsStateWithLifecycle(STATE_IDLE)
            Text(
                text = playback.displayText,
                color = Color.White
            )
            val exception by state.exception.collectAsStateWithLifecycle(null)
            Text(
                text = exception.displayText,
                color = LocalTheme.current.error
            )
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
        STATE_READY -> R.string.playback_state_ready
        STATE_ENDED -> R.string.playback_state_ended
        else -> error("invalidate playback state: $this")
    }.let { stringResource(id = it) }
