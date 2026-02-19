package com.m3u.tv.screens.playlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.m3u.business.playlist.PlaylistViewModel
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.core.foundation.components.CircularProgressIndicator
import com.m3u.data.database.model.Channel
import com.m3u.i18n.R
import com.m3u.tv.screens.dashboard.rememberChildPadding
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun PlaylistScreen(
    playlistTabFocusRequester: FocusRequester?,
    onChannelClick: (channel: Channel) -> Unit,
    onChannelLongClick: (channel: Channel) -> Unit,
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    isTopBarVisible: Boolean,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val preferences = hiltPreferences()
    val playlistUrl by viewModel.playlistUrl.collectAsStateWithLifecycle()
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val refreshing by viewModel.subscribingOrRefreshing.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()

    val isLoading by remember {
        derivedStateOf {
            playlistUrl.isNotEmpty() && (playlist == null || refreshing)
        }
    }
    val isEmpty by remember {
        derivedStateOf {
            playlistUrl.isEmpty() || (playlist == null && !refreshing)
        }
    }

    when {
        isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        isEmpty -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(
                        if (playlistUrl.isEmpty()) R.string.feat_playlist_no_playlist_open
                        else R.string.feat_playlist_error_playlist_url_not_found
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        else -> {
            // Fix 1A: collect all paging flows here in composable scope, not inside LazyListScope
            val pagingChannels: List<Pair<PlaylistViewModel.CategoryWithChannels, LazyPagingItems<Channel>>> =
                channels.map { it to it.channels.collectAsLazyPagingItems() }

            Catalog(
                channels = pagingChannels,
                onChannelClick = onChannelClick,
                onChannelLongClick = onChannelLongClick,
                onScroll = onScroll,
                isTopBarVisible = isTopBarVisible,
                playlistTabFocusRequester = playlistTabFocusRequester,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun Catalog(
    channels: List<Pair<PlaylistViewModel.CategoryWithChannels, LazyPagingItems<Channel>>>,
    onChannelClick: (channel: Channel) -> Unit,
    onChannelLongClick: (channel: Channel) -> Unit,
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    isTopBarVisible: Boolean,
    playlistTabFocusRequester: FocusRequester?,
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
        modifier = modifier.then(
            if (playlistTabFocusRequester != null) {
                Modifier.focusProperties {
                    up = playlistTabFocusRequester
                }
            } else {
                Modifier
            }
        ),
        state = lazyListState,
        contentPadding = PaddingValues(top = childPadding.top, bottom = 104.dp)
    ) {
        channelGallery(
            channels = channels,
            onChannelClick = onChannelClick,
            onChannelLongClick = onChannelLongClick,
            startPadding = childPadding.start,
            endPadding = childPadding.end,
            itemWidth = itemWidth,
        )
    }
}
