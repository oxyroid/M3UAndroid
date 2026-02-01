package com.m3u.tv.screens.favorite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.m3u.business.favorite.FavoriteViewModel
import com.m3u.data.database.model.Channel
import com.m3u.tv.screens.dashboard.rememberChildPadding

@Composable
fun FavoriteScreen(
    onChannelClick: (channel: Channel) -> Unit,
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    isTopBarVisible: Boolean,
    viewModel: FavoriteViewModel = hiltViewModel(),
) {
    val channels = viewModel.channels.collectAsLazyPagingItems()
    Catalog(
        channels = channels,
        onChannelClick = onChannelClick,
        isTopBarVisible = isTopBarVisible,
        onScroll = onScroll,
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun Catalog(
    channels: LazyPagingItems<Channel>,
    onChannelClick: (channel: Channel) -> Unit,
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    isTopBarVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val childPadding = rememberChildPadding()
    val lazyListState = rememberLazyStaggeredGridState()
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

    LazyVerticalStaggeredGrid(
        modifier = modifier,
        state = lazyListState,
        columns = StaggeredGridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(
            top = childPadding.top,
            bottom = 104.dp,
            start = childPadding.start,
            end = childPadding.end
        )
    ) {
        favoriteGallery(
            channels = channels,
            onChannelClick = onChannelClick
        )
    }
}
