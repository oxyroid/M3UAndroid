package com.m3u.tv.screens.playlist

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    isTopBarVisible: Boolean,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    Catalog(
        channels = channels,
        popularFilmsThisWeek = emptyList(),
        onChannelClick = onChannelClick,
        onScroll = onScroll,
        isTopBarVisible = isTopBarVisible,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun Catalog(
    channels: List<PlaylistViewModel.CategoryWithChannels>,
    popularFilmsThisWeek: List<Channel>,
    onChannelClick: (channel: Channel) -> Unit,
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    isTopBarVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val childPadding = rememberChildPadding()
    val lazyListState = rememberLazyListState()
    val shouldShowTopBar by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 &&
                    lazyListState.firstVisibleItemScrollOffset == 0
        }
    }

    LaunchedEffect(shouldShowTopBar) {
        onScroll(shouldShowTopBar)
    }
    LaunchedEffect(isTopBarVisible) {
        if (isTopBarVisible) lazyListState.animateScrollToItem(0)
    }
    LazyColumn(
        modifier = modifier,
        state = lazyListState,
        contentPadding = PaddingValues(top = childPadding.top, bottom = 104.dp)
    ) {
        channelGallery(
            channels = channels,
            onChannelClick = onChannelClick,
            startPadding = childPadding.start,
            endPadding = childPadding.end
        )
    }
}
