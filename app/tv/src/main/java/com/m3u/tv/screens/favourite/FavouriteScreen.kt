package com.m3u.tv.screens.favourite

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.business.favorite.FavouriteViewModel
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.core.foundation.components.CircularProgressIndicator
import com.m3u.core.wrapper.Resource
import com.m3u.data.database.model.Channel
import com.m3u.i18n.R
import com.m3u.tv.screens.dashboard.rememberChildPadding
import com.m3u.tv.screens.playlist.favouriteChannelGallery
import com.m3u.tv.screens.playlist.playlistItemWidthForSize
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun FavouriteScreen(
    onChannelClick: (Channel) -> Unit,
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    isTopBarVisible: Boolean,
    viewModel: FavouriteViewModel = hiltViewModel(),
) {
    val channelsResource by viewModel.channels.collectAsStateWithLifecycle()
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
        if (isTopBarVisible) {
            lazyListState.animateScrollToItem(0)
        }
    }

    when (val channels = channelsResource) {
        Resource.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        is Resource.Success -> {
            LazyColumn(
                state = lazyListState,
                contentPadding = PaddingValues(
                    top = childPadding.top,
                    bottom = 104.dp
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                favouriteChannelGallery(
                    channels = channels.data,
                    onChannelClick = onChannelClick,
                    startPadding = childPadding.start,
                    endPadding = childPadding.end,
                    itemWidth = itemWidth
                )
            }
        }

        is Resource.Failure -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.ui_error_unknown),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

