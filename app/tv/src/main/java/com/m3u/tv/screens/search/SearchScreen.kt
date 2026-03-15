package com.m3u.tv.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.m3u.core.util.basic.title
import com.m3u.data.database.model.Channel
import com.m3u.i18n.R
import com.m3u.tv.screens.dashboard.rememberChildPadding
import com.m3u.tv.screens.playlist.ChannelGalleryItem
import com.m3u.tv.ui.component.TextField

@Composable
fun SearchScreen(
    onChannelClick: (channel: Channel) -> Unit,
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    searchScreenViewModel: SearchScreenViewModel = hiltViewModel(),
) {
    val channels = searchScreenViewModel.channels.collectAsLazyPagingItems()

    SearchResult(
        searchQuery = searchScreenViewModel.searchQuery,
        channels = channels,
        onChannelClick = onChannelClick,
        onScroll = onScroll,
        modifier = Modifier.fillMaxSize()
    )
}

private const val SEARCH_GRID_CELL_MIN_DP = 260

@Composable
fun SearchResult(
    searchQuery: MutableState<String>,
    channels: LazyPagingItems<Channel>,
    onChannelClick: (channel: Channel) -> Unit,
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val childPadding = rememberChildPadding()
    val gridState = rememberLazyGridState()
    val shouldShowTopBar by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex == 0 &&
                gridState.firstVisibleItemScrollOffset == 0
        }
    }

    LaunchedEffect(shouldShowTopBar) {
        onScroll(shouldShowTopBar)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextField(
            value = searchQuery.value,
            onValueChange = { searchQuery.value = it },
            placeholder = stringResource(R.string.feat_setting_placeholder_title).title(),
            modifier = Modifier
                .padding(
                    start = childPadding.start,
                    end = childPadding.end
                )
        )

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(SEARCH_GRID_CELL_MIN_DP.dp),
            contentPadding = PaddingValues(
                start = childPadding.start,
                end = childPadding.end,
                bottom = 104.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(
                count = channels.itemCount,
                key = { index -> "search_channel_$index" },
            ) { index ->
                val channel = channels[index]
                if (channel != null) {
                    ChannelGalleryItem(
                        channel = channel,
                        modifier = Modifier.fillMaxWidth(),
                        itemWidth = null,
                        onChannelClick = onChannelClick,
                        onChannelLongClick = { },
                    )
                }
            }
        }
    }
}
