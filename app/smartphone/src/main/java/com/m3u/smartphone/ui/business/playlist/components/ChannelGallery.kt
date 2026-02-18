package com.m3u.smartphone.ui.business.playlist.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Programme
import com.m3u.business.playlist.PlaylistViewModel
import com.m3u.core.foundation.components.CircularProgressIndicator
import com.m3u.smartphone.ui.material.components.VerticalDraggableScrollbar
import com.m3u.smartphone.ui.material.ktx.plus
import com.m3u.smartphone.ui.material.model.LocalSpacing

@Composable
internal fun ChannelGallery(
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
                    ChannelItem(
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

/**
 * Placeholder grid shown while [PlaylistViewModel.channels] is still loading (empty categories).
 * Uses an infinite alpha pulse to indicate loading activity without a spinner.
 */
@Composable
internal fun SkeletonChannelGrid(
    rowCount: Int,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val spacing = LocalSpacing.current
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )
    // Generate enough placeholder items to fill the visible area
    val placeholderCount = rowCount * 6
    val placeholders = remember(placeholderCount) { List(placeholderCount) { it } }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(rowCount),
        verticalItemSpacing = spacing.medium,
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        contentPadding = PaddingValues(spacing.medium) + contentPadding,
        userScrollEnabled = false,
        modifier = modifier
            .fillMaxSize()
            .alpha(alpha)
    ) {
        items(placeholders) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            )
        }
    }
}
