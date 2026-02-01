package com.m3u.tv.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.m3u.core.util.basic.title
import com.m3u.data.database.model.Channel
import com.m3u.i18n.R
import com.m3u.tv.common.ChannelsRow
import com.m3u.tv.screens.dashboard.rememberChildPadding
import com.m3u.tv.ui.component.TextField

@Composable
fun SearchScreen(
    onChannelClick: (channel: Channel) -> Unit,
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    searchScreenViewModel: SearchScreenViewModel = hiltViewModel(),
) {
    val lazyColumnState = rememberLazyListState()
    val shouldShowTopBar by remember {
        derivedStateOf {
            lazyColumnState.firstVisibleItemIndex == 0 &&
                    lazyColumnState.firstVisibleItemScrollOffset < 100
        }
    }

    val channels = searchScreenViewModel.channels.collectAsLazyPagingItems()

    LaunchedEffect(shouldShowTopBar) {
        onScroll(shouldShowTopBar)
    }
    SearchResult(
        searchQuery = searchScreenViewModel.searchQuery,
        channels = channels,
        onChannelClick = onChannelClick,
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun SearchResult(
    searchQuery: MutableState<String>,
    channels: LazyPagingItems<Channel>,
    onChannelClick: (channel: Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val childPadding = rememberChildPadding()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextField(
            value = searchQuery.value,
            onValueChange = { searchQuery.value = it },
            placeholder = stringResource(R.string.feat_playlist_query_placeholder).title(),
            modifier = Modifier
                .padding(
                    start = childPadding.start,
                    end = childPadding.end
                )
        )

        ChannelsRow(
            channels = channels,
            onChannelSelected = { selectedChannel -> onChannelClick(selectedChannel) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}
