package com.m3u.features.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.util.toast
import com.m3u.ui.components.LivePlayer

@Composable
internal fun LiveRoute(
    modifier: Modifier = Modifier,
    viewModel: LiveViewModel = hiltViewModel(),
    id: Int
) {

    val context = LocalContext.current
    val state: LiveState by viewModel.readable.collectAsStateWithLifecycle()
    LaunchedEffect(state.message) {
        state.message.handle {
            context.toast(it)
        }
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("features:live")
    ) {
        LivePlayer(
            url = url,
            useController = false,
            modifier = Modifier.fillMaxSize()
        )
    }
}