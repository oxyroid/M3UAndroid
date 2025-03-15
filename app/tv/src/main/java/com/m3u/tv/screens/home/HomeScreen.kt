package com.m3u.tv.screens.home

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Card
import androidx.tv.material3.CompactCard
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.m3u.business.foryou.ForyouViewModel
import com.m3u.business.foryou.Recommend
import com.m3u.core.foundation.components.CircularProgressIndicator
import com.m3u.core.wrapper.Resource
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.PlaylistWithCount
import com.m3u.tv.common.ChannelsRow
import com.m3u.tv.screens.dashboard.rememberChildPadding

@Composable
fun HomeScreen(
    onChannelClick: (channel: Channel) -> Unit,
    goToChannel: (playlistUrl: String) -> Unit,
    goToVideoPlayer: (channel: Channel) -> Unit,
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    isTopBarVisible: Boolean,
    viewModel: ForyouViewModel = hiltViewModel(),
) {
    val playlists: Resource<List<PlaylistWithCount>> by viewModel.playlists.collectAsStateWithLifecycle()
    val specs: List<Recommend.Spec> by viewModel.specs.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        when (val playlists = playlists) {
            Resource.Loading -> {
                CircularProgressIndicator(
                    Modifier.align(Alignment.Center)
                )
            }
            is Resource.Success -> {
                Catalog(
                    playlists = playlists.data,
                    specs = specs,
                    trendingChannels = emptyList(),
                    top10Channels = emptyList(),
                    nowPlayingChannels = emptyList(),
                    onChannelClick = onChannelClick,
                    onScroll = onScroll,
                    goToChannel = goToChannel,
                    goToVideoPlayer = goToVideoPlayer,
                    isTopBarVisible = isTopBarVisible
                )
            }
            is Resource.Failure -> {
                Text(
                    text = playlists.message.orEmpty()
                )
            }
        }
    }
}

@Composable
private fun Catalog(
    playlists: List<PlaylistWithCount>,
    specs: List<Recommend.Spec>,
    trendingChannels: List<Channel>,
    top10Channels: List<Channel>,
    nowPlayingChannels: List<Channel>,
    onChannelClick: (channel: Channel) -> Unit,
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    goToChannel: (playlistUrl: String) -> Unit,
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
        modifier = modifier
    ) {
        item(contentType = "FeaturedChannelsCarousel") {
            FeaturedSpecsCarousel(
                specs = specs,
                padding = childPadding,
                onClickSpec = { spec ->
                    when (spec) {
                        is Recommend.UnseenSpec -> {
                            goToVideoPlayer(spec.channel)
                        }
                        is Recommend.DiscoverSpec -> TODO()
                        is Recommend.NewRelease -> TODO()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(324.dp)
                /*
                 Setting height for the FeaturedChannelCarousel to keep it rendered with same height,
                 regardless of the top bar's visibility
                 */
            )
        }
        item(contentType = "PlaylistsRow") {
            val startPadding: Dp = rememberChildPadding().start
            val endPadding: Dp = rememberChildPadding().end
            Text(
                text = "Playlist",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 30.sp
                ),
                modifier = Modifier
                    .alpha(1f)
                    .padding(start = startPadding, top = 16.dp, bottom = 16.dp)
            )
            LazyRow(
                modifier = Modifier
                    .focusGroup()
                    .padding(top = 16.dp),
                contentPadding = PaddingValues(start = startPadding, end = endPadding)
            ) {
                items(playlists) { (playlist, count) ->
                    CompactCard(
                        onClick = {
                            goToChannel(playlist.url)
                        },
                        title = {
                            Text(
                                text = playlist.title,
                                modifier = Modifier.padding(16.dp)
                            )
                        },
                        image = {},
                        modifier = Modifier
                            .width(325.dp)
                            .aspectRatio(2f)
                    )
                }
            }
        }
        item(contentType = "ChannelsRow") {
            ChannelsRow(
                modifier = Modifier
                    .padding(top = 16.dp),
                channels = trendingChannels,
                title = "StringConstants.Composable.HomeScreenTrendingTitle",
                onChannelSelected = onChannelClick
            )
        }
//        item(contentType = "Top10ChannelsList") {
//            Top10ChannelsList(
//                channels = top10Channels,
//                onChannelClick = onChannelClick,
//                modifier = Modifier.onFocusChanged {
//                    immersiveListHasFocus = it.hasFocus
//                },
//            )
//        }
        item(contentType = "ChannelsRow") {
            ChannelsRow(
                modifier = Modifier
                    .padding(top = 16.dp),
                channels = nowPlayingChannels,
                title = "StringConstants.Composable.HomeScreenNowPlayingChannelsTitle",
                onChannelSelected = onChannelClick
            )
        }
    }
}
