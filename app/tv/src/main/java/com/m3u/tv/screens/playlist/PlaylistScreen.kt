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
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.m3u.business.playlist.PlaylistViewModel
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.data.database.model.Channel
import com.m3u.tv.screens.dashboard.rememberChildPadding

@Composable
fun PlaylistScreen(
    onChannelClick: (channel: Channel) -> Unit,
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    isTopBarVisible: Boolean,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val preferences = hiltPreferences()
    val playlistUrl by viewModel.playlistUrl.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()

    // Fix 2: gate auto-refresh with the 6h timestamp, same as smartphone
    LaunchedEffect(preferences.autoRefreshChannels, playlistUrl) {
        if (playlistUrl.isNotEmpty() && preferences.autoRefreshChannels) {
            viewModel.refresh()
        }
    }

    // Fix 1A: collect all paging flows here in composable scope, not inside LazyListScope
    val pagingChannels: List<Pair<PlaylistViewModel.CategoryWithChannels, LazyPagingItems<Channel>>> =
        channels.map { it to it.channels.collectAsLazyPagingItems() }

    Catalog(
        channels = pagingChannels,
        onChannelClick = onChannelClick,
        onScroll = onScroll,
        isTopBarVisible = isTopBarVisible,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun Catalog(
    channels: List<Pair<PlaylistViewModel.CategoryWithChannels, LazyPagingItems<Channel>>>,
    onChannelClick: (channel: Channel) -> Unit,
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    isTopBarVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val preferences = hiltPreferences()
    val childPadding = rememberChildPadding()
    val itemWidth = playlistItemWidthForSize(preferences.playlistItemSize)
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
            endPadding = childPadding.end,
            itemWidth = itemWidth,
        )
    }
}
