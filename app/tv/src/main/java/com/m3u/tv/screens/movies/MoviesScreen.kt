package com.m3u.tv.screens.channels

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.data.database.model.Channel
import com.m3u.tv.common.Loading
import com.m3u.tv.common.ChannelsRow
import com.m3u.tv.screens.dashboard.rememberChildPadding
import com.m3u.tv.screens.movies.ChannelsScreenList
import com.m3u.tv.screens.movies.ChannelsScreenUiState
import com.m3u.tv.screens.movies.ChannelsScreenViewModel

@Composable
fun ChannelsScreen(
    onChannelClick: (channel: Channel) -> Unit,
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    isTopBarVisible: Boolean,
    channelsScreenViewModel: ChannelsScreenViewModel = hiltViewModel(),
) {
    val uiState by remember { mutableStateOf(ChannelsScreenUiState.Loading) }
//    val uiState by channelsScreenViewModel.uiState.collectAsStateWithLifecycle()
    when (val s = uiState) {
        is ChannelsScreenUiState.Loading -> Loading()
        is ChannelsScreenUiState.Ready -> {
            Catalog(
                channels = s.channels,
                popularFilmsThisWeek = s.popularFilmsThisWeek,
                onChannelClick = onChannelClick,
                onScroll = onScroll,
                isTopBarVisible = isTopBarVisible,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun Catalog(
    channels: List<Channel>,
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
        item {
            ChannelsScreenList(
                channels = channels,
                onChannelClick = onChannelClick
            )
        }
        item {
            ChannelsRow(
                modifier = Modifier.padding(top = childPadding.top),
                title = "PopularFilmsThisWeekTitle",
                channels = popularFilmsThisWeek,
                onChannelSelected = onChannelClick
            )
        }
    }
}
