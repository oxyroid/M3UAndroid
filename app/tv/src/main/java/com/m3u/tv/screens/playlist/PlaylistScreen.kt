package com.m3u.tv.screens.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
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
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.m3u.business.playlist.PlaylistViewModel
import com.m3u.core.foundation.components.CircularProgressIndicator
import com.m3u.data.database.model.Channel
import com.m3u.i18n.R
import com.m3u.tv.screens.dashboard.rememberChildPadding
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

private const val GRID_COLUMNS = 4
private const val GRID_CELL_MIN_DP = 260

@Composable
fun PlaylistScreen(
    playlistTabFocusRequester: FocusRequester?,
    onChannelClick: (channel: Channel) -> Unit,
    onChannelLongClick: (channel: Channel) -> Unit,
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    isTopBarVisible: Boolean,
    viewModelStoreOwner: ViewModelStoreOwner? = null,
    viewModelKey: String? = null,
    viewModel: PlaylistViewModel = when {
        viewModelStoreOwner != null && viewModelKey != null ->
            hiltViewModel(viewModelStoreOwner, viewModelKey)
        else -> hiltViewModel()
    },
) {
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

private sealed interface PlaylistCatalogItem {
    data class SectionHeader(val category: String) : PlaylistCatalogItem
    data class GridRowChunk(val categoryIndex: Int, val startIndex: Int) : PlaylistCatalogItem
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
    val childPadding = rememberChildPadding()
    val focusModifier = if (playlistTabFocusRequester != null) {
        Modifier.focusProperties { up = playlistTabFocusRequester }
    } else Modifier

    if (channels.size == 1 && channels[0].first.category.isBlank()) {
        SingleCategoryGrid(
            pagingChannels = channels[0].second,
            onChannelClick = onChannelClick,
            onChannelLongClick = onChannelLongClick,
            onScroll = onScroll,
            isTopBarVisible = isTopBarVisible,
            modifier = modifier.then(focusModifier),
        )
    } else {
        SectionedCatalog(
            channels = channels,
            onChannelClick = onChannelClick,
            onChannelLongClick = onChannelLongClick,
            onScroll = onScroll,
            isTopBarVisible = isTopBarVisible,
            modifier = modifier.then(focusModifier),
        )
    }
}

@Composable
private fun SingleCategoryGrid(
    pagingChannels: LazyPagingItems<Channel>,
    onChannelClick: (Channel) -> Unit,
    onChannelLongClick: (Channel) -> Unit,
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    isTopBarVisible: Boolean,
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

    LaunchedEffect(shouldShowTopBar) { onScroll(shouldShowTopBar) }
    LaunchedEffect(isTopBarVisible) {
        if (isTopBarVisible) gridState.animateScrollToItem(0)
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(GRID_CELL_MIN_DP.dp),
        contentPadding = PaddingValues(
            start = childPadding.start,
            end = childPadding.end,
            bottom = 104.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(
            count = pagingChannels.itemCount,
            key = { index -> "channel_$index" },
        ) { index ->
            val channel = pagingChannels[index]
            if (channel != null) {
                ChannelGalleryItem(
                    channel = channel,
                    modifier = Modifier.fillMaxWidth(),
                    itemWidth = null,
                    onChannelClick = onChannelClick,
                    onChannelLongClick = onChannelLongClick,
                )
            }
        }
    }
}

@Composable
private fun SectionedCatalog(
    channels: List<Pair<PlaylistViewModel.CategoryWithChannels, LazyPagingItems<Channel>>>,
    onChannelClick: (channel: Channel) -> Unit,
    onChannelLongClick: (channel: Channel) -> Unit,
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

    LaunchedEffect(shouldShowTopBar) { onScroll(shouldShowTopBar) }
    LaunchedEffect(isTopBarVisible) {
        if (isTopBarVisible) lazyListState.animateScrollToItem(0)
    }

    val flattenedItems = buildList {
        channels.forEachIndexed { categoryIndex, (cwc, paging) ->
            if (cwc.category.isNotBlank()) {
                add(PlaylistCatalogItem.SectionHeader(cwc.category))
            }
            val itemCount = paging.itemCount
            var startIndex = 0
            while (startIndex < itemCount) {
                add(PlaylistCatalogItem.GridRowChunk(categoryIndex, startIndex))
                startIndex += GRID_COLUMNS
            }
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier,
        contentPadding = PaddingValues(top = childPadding.top, bottom = 104.dp),
    ) {
        items(
            items = flattenedItems,
            key = { item ->
                when (item) {
                    is PlaylistCatalogItem.SectionHeader -> "header_${item.category}"
                    is PlaylistCatalogItem.GridRowChunk -> "chunk_${item.categoryIndex}_${item.startIndex}"
                }
            },
        ) { item ->
            when (item) {
                is PlaylistCatalogItem.SectionHeader -> PlaylistSectionHeader(
                    category = item.category,
                    startPadding = childPadding.start,
                    endPadding = childPadding.end,
                )
                is PlaylistCatalogItem.GridRowChunk -> {
                    val (_, paging) = channels[item.categoryIndex]
                    PlaylistGridRowChunk(
                        categoryIndex = item.categoryIndex,
                        startIndex = item.startIndex,
                        columns = GRID_COLUMNS,
                        pagingChannels = paging,
                        startPadding = childPadding.start,
                        endPadding = childPadding.end,
                        onChannelClick = onChannelClick,
                        onChannelLongClick = onChannelLongClick,
                    )
                }
            }
        }
    }
}
