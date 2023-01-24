package com.m3u.features.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.data.entity.Live
import com.m3u.ui.components.LivePlayer

@Composable
internal fun LiveRoute(
    modifier: Modifier = Modifier,
    viewModel: LiveViewModel = hiltViewModel(),
    live: Live
) {
    LaunchedEffect(live) {
        viewModel.onEvent(LiveEvent.Init(live))
    }
    val state: LiveState by viewModel.readable.collectAsStateWithLifecycle()
    LiveScreen(
        modifier = modifier,
        url = live.url
    )
}

@Composable
private fun LiveScreen(
    modifier: Modifier = Modifier,
    url: String
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("features:live")
    ) {
        LivePlayer(
            url = url,
            useController = true,
            modifier = Modifier.fillMaxSize()
        )
    }
}