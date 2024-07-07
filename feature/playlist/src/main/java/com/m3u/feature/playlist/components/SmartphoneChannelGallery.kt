package com.m3u.feature.playlist.components

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Programme
import com.m3u.feature.playlist.PlaylistViewModel
import com.m3u.material.components.CircularProgressIndicator
import com.m3u.material.components.VerticalDraggableScrollbar
import com.m3u.material.ktx.plus
import com.m3u.material.model.LocalSpacing

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
    getProgrammeCurrently: suspend (channelId: String) -> Programme?,
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
                    var programme: Programme? by remember { mutableStateOf(null) }
                    LaunchedEffect(channel.originalId) {
                        programme = getProgrammeCurrently(channel.originalId.orEmpty())
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
