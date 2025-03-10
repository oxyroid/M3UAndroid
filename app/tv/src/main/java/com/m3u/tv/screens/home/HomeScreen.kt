package com.m3u.tv.screens.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.data.database.model.Channel
import com.m3u.tv.common.Error
import com.m3u.tv.common.Loading
import com.m3u.tv.common.ChannelsRow
import com.m3u.tv.screens.dashboard.rememberChildPadding

@Composable
fun HomeScreen(
    onChannelClick: (channel: Channel) -> Unit,
    goToVideoPlayer: (channel: Channel) -> Unit,
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    isTopBarVisible: Boolean,
    homeScreeViewModel: HomeScreeViewModel = hiltViewModel(),
) {
//    val uiState by homeScreeViewModel.uiState.collectAsStateWithLifecycle()
    val uiState by remember { mutableStateOf(HomeScreenUiState.Loading) }

    when (val s = uiState) {
        is HomeScreenUiState.Ready -> {
            Catalog(
                featuredChannels = s.featuredChannels,
                trendingChannels = s.trendingChannels,
                top10Channels = s.top10Channels,
                nowPlayingChannels = s.nowPlayingChannels,
                onChannelClick = onChannelClick,
                onScroll = onScroll,
                goToVideoPlayer = goToVideoPlayer,
                isTopBarVisible = isTopBarVisible,
                modifier = Modifier.fillMaxSize(),
            )
        }

        is HomeScreenUiState.Loading -> Loading(modifier = Modifier.fillMaxSize())
        is HomeScreenUiState.Error -> Error(modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun Catalog(
    featuredChannels: List<Channel>,
    trendingChannels: List<Channel>,
    top10Channels: List<Channel>,
    nowPlayingChannels: List<Channel>,
    onChannelClick: (channel: Channel) -> Unit,
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    goToVideoPlayer: (channel: Channel) -> Unit,
    modifier: Modifier = Modifier,
    isTopBarVisible: Boolean = true,
) {

    val lazyListState = rememberLazyListState()
    val childPadding = rememberChildPadding()
    var immersiveListHasFocus by remember { mutableStateOf(false) }

    val shouldShowTopBar by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 &&
                lazyListState.firstVisibleItemScrollOffset < 300
        }
    }

    LaunchedEffect(shouldShowTopBar) {
        onScroll(shouldShowTopBar)
    }
    LaunchedEffect(isTopBarVisible) {
        if (isTopBarVisible) lazyListState.animateScrollToItem(0)
    }

    LazyColumn(
        state = lazyListState,
        contentPadding = PaddingValues(bottom = 108.dp),
        // Setting overscan margin to bottom to ensure the last row's visibility
        modifier = modifier,
    ) {

        item(contentType = "FeaturedChannelsCarousel") {
            FeaturedChannelsCarousel(
                channels = featuredChannels,
                padding = childPadding,
                goToVideoPlayer = goToVideoPlayer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(324.dp)
                /*
                 Setting height for the FeaturedChannelCarousel to keep it rendered with same height,
                 regardless of the top bar's visibility
                 */
            )
        }
        item(contentType = "ChannelsRow") {
            ChannelsRow(
                modifier = Modifier.padding(top = 16.dp),
                channels = trendingChannels,
                title = "StringConstants.Composable.HomeScreenTrendingTitle",
                onChannelSelected = onChannelClick
            )
        }
        item(contentType = "Top10ChannelsList") {
            Top10ChannelsList(
                channels = top10Channels,
                onChannelClick = onChannelClick,
                modifier = Modifier.onFocusChanged {
                    immersiveListHasFocus = it.hasFocus
                },
            )
        }
        item(contentType = "ChannelsRow") {
            ChannelsRow(
                modifier = Modifier.padding(top = 16.dp),
                channels = nowPlayingChannels,
                title = "StringConstants.Composable.HomeScreenNowPlayingChannelsTitle",
                onChannelSelected = onChannelClick
            )
        }
    }
}
