package com.m3u.feature.playlist.components

import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import com.m3u.core.architecture.preferences.Preferences
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.core.wrapper.Event
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Programme
import com.m3u.feature.playlist.PlaylistViewModel
import com.m3u.material.components.CircularProgressIndicator
import com.m3u.material.components.VerticalDraggableScrollbar
import com.m3u.material.ktx.isAtTop
import com.m3u.material.ktx.plus
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.EventHandler
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
internal fun SmartphoneChannelGallery(
    state: LazyStaggeredGridState,
    rowCount: Int,
    categoryWithChannels: PlaylistViewModel.CategoryWithChannels?,
    zapping: Channel?,
    recently: Boolean,
    isVodOrSeriesPlaylist: Boolean,
    onClick: (Channel) -> Unit,
    onLongClick: (Channel) -> Unit,
    getProgrammeCurrently: suspend (channelId: Int) -> Programme?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val spacing = LocalSpacing.current
    val preferences = hiltPreferences()

    val actualRowCount = when {
        preferences.noPictureMode -> rowCount
        isVodOrSeriesPlaylist -> rowCount + 2
        else -> rowCount
    }

    val channels = categoryWithChannels?.channels?.collectAsLazyPagingItems()

    val currentGetProgrammeCurrently by rememberUpdatedState(getProgrammeCurrently)

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(start = spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        LazyVerticalStaggeredGrid(
            state = state,
            columns = StaggeredGridCells.Fixed(actualRowCount),
            verticalItemSpacing = spacing.medium,
            horizontalArrangement = Arrangement.spacedBy(spacing.medium),
            contentPadding = PaddingValues(vertical = spacing.medium) + contentPadding,
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            items(channels?.itemCount ?: 0) { index ->
                val channel = channels?.get(index)
                if (channel != null) {
                    val programme: Programme? by produceState<Programme?>(
                        initialValue = null,
                        key1 = channel.id
                    ) {
                        value = currentGetProgrammeCurrently(channel.id)
                    }
                    SmartphoneChannelItem(
                        channel = channel,
                        programme = programme,
                        recently = recently,
                        zapping = zapping == channel,
                        isVodOrSeriesPlaylist = isVodOrSeriesPlaylist,
                        onClick = { onClick(channel) },
                        onLongClick = { onLongClick(channel) },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
        VerticalDraggableScrollbar(
            lazyStaggeredGridState = state
        )
    }
}

@Composable
internal fun SmartphoneChannelGalleryEffect(
    listState: LazyStaggeredGridState,
    pagerState: PagerState,
    isAtTopState: MutableState<Boolean>,
    scrollUp: Event<Unit>,
    page: Int
) {
    val isSettled by remember {
        derivedStateOf { pagerState.settledPage == page }
    }
    if (isSettled) {
        LaunchedEffect(listState, isAtTopState) {
            snapshotFlow { listState.isAtTop }
                .onEach { isAtTopState.value = it }
                .launchIn(this)
        }
    }
    EventHandler(scrollUp) {
        listState.scrollToItem(0)
    }
}

@Composable
internal fun rememberRowCountState(): State<Int> {
    val configuration = LocalConfiguration.current
    val preferences = hiltPreferences()

    val orientation = configuration.orientation
    val rowCount = preferences.rowCount

    return produceState(
        initialValue = Preferences.DEFAULT_ROW_COUNT,
        key1 = orientation,
        key2 = rowCount
    ) {
        value = when (orientation) {
            ORIENTATION_LANDSCAPE -> rowCount + 2
            ORIENTATION_PORTRAIT -> rowCount
            else -> rowCount
        }
    }
}