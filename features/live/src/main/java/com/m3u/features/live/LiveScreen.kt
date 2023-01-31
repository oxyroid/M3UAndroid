package com.m3u.features.live

import androidx.compose.foundation.background
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.m3u.core.util.toast
import com.m3u.ui.components.LivePlayer
import com.m3u.ui.components.M3UColumn
import com.m3u.ui.components.rememberPlayerState
import com.m3u.ui.model.AppAction
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
    LifecycleEffect { event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                val actions = listOf<AppAction>()
                setAppActionsUpdated(actions)
            }

            Lifecycle.Event.ON_PAUSE -> {
                setAppActionsUpdated(emptyList())
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
            val exception by state.exceptionSource.collectAsStateWithLifecycle(null)
            Text(
                text = exception?.let {
                    "Error: [${it.errorCode}] ${it.errorCodeName}"
                }.orEmpty(),
                color = Color.White
            )
            val playerState by state.stateSource.collectAsStateWithLifecycle(Player.STATE_IDLE)
            Text(
                text = "State: $playerState",
                color = Color.White
            )
        }
    }
}