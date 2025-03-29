package com.m3u.tv.screens.search

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
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

    val searchState by searchScreenViewModel.searchState.collectAsStateWithLifecycle()

    LaunchedEffect(shouldShowTopBar) {
        onScroll(shouldShowTopBar)
    }

    when (val s = searchState) {
        is SearchState.Searching -> {
            Text(text = "Searching...")
        }

        is SearchState.Done -> {
            val channels = s.channels
            SearchResult(
                channels = channels,
                searchChannels = searchScreenViewModel::query,
                onChannelClick = onChannelClick,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SearchResult(
    channels: List<Channel>,
    searchChannels: (queryString: String) -> Unit,
    onChannelClick: (channel: Channel) -> Unit,
    modifier: Modifier = Modifier,
    lazyColumnState: LazyListState = rememberLazyListState(),
) {
    val childPadding = rememberChildPadding()
    var searchQuery by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier,
        state = lazyColumnState
    ) {
        item {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = stringResource(R.string.feat_setting_placeholder_title),
                keyboardActions = KeyboardActions(
                    onSearch = { searchChannels(searchQuery) }
                ),
                modifier = Modifier.padding(start = childPadding.start)
            )
        }

        item {
            ChannelsRow(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = childPadding.top * 2),
                channels = channels
            ) { selectedChannel -> onChannelClick(selectedChannel) }
        }
    }
}
