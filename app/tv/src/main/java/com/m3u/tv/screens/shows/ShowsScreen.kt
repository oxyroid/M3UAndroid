package com.m3u.tv.screens.shows

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
import com.m3u.data.database.model.Channel
import com.m3u.tv.common.ChannelsRow
import com.m3u.tv.common.Loading
import com.m3u.tv.screens.dashboard.rememberChildPadding

@Composable
fun ShowsScreen(
    onTVShowClick: (channel: Channel) -> Unit,
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    isTopBarVisible: Boolean,
    showScreenViewModel: ShowScreenViewModel = hiltViewModel(),
) {
//    val uiState = showScreenViewModel.uiState.collectAsStateWithLifecycle()
    val uiState by remember { mutableStateOf(ShowScreenUiState.Loading) }
    when (val currentState = uiState) {
        is ShowScreenUiState.Loading -> {
            Loading(modifier = Modifier.fillMaxSize())
        }

        is ShowScreenUiState.Ready -> {
            Catalog(
                tvShowList = currentState.tvShowList,
                bingeWatchDramaList = currentState.bingeWatchDramaList,
                onTVShowClick = onTVShowClick,
                onScroll = onScroll,
                isTopBarVisible = isTopBarVisible,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun Catalog(
    tvShowList: List<Channel>,
    bingeWatchDramaList: List<Channel>,
    onTVShowClick: (channel: Channel) -> Unit,
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    isTopBarVisible: Boolean,
    modifier: Modifier = Modifier
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
//            ChannelsScreenList(
//                channels = tvShowList,
//                onChannelClick = onTVShowClick
//            )
        }
        item {
            ChannelsRow(
                modifier = Modifier.padding(top = childPadding.top),
                title = "BingeWatchDramasTitle",
                channels = bingeWatchDramaList,
                onChannelSelected = onTVShowClick
            )
        }
    }
}
