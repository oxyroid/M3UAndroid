package com.m3u.tv.screens.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.business.playlist.PlaylistViewModel
import com.m3u.data.database.model.Channel
import com.m3u.tv.screens.dashboard.rememberChildPadding

@Composable
fun PlaylistScreen(
    onChannelClick: (channel: Channel) -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    Catalog(
        channels = channels,
        onChannelClick = onChannelClick,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun Catalog(
    channels: List<PlaylistViewModel.CategoryWithChannels>,
    onChannelClick: (channel: Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val childPadding = rememberChildPadding()
    val lazyListState = rememberLazyListState()
    LazyColumn(
        modifier = modifier,
        state = lazyListState,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = childPadding.top, bottom = 104.dp)
    ) {
        channelGallery(
            channels = channels,
            onChannelClick = onChannelClick,
            startPadding = childPadding.start,
            endPadding = childPadding.end,
        )
    }
}
