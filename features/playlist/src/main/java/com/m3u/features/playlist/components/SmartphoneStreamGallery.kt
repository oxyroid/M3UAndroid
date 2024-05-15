package com.m3u.features.playlist.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.data.database.model.Programme
import com.m3u.data.database.model.Stream
import com.m3u.features.playlist.PlaylistViewModel
import com.m3u.material.components.CircularProgressIndicator
import com.m3u.material.ktx.plus
import com.m3u.material.model.LocalSpacing

@Composable
internal fun SmartphoneStreamGallery(
    state: LazyStaggeredGridState,
    rowCount: Int,
    channel: PlaylistViewModel.Channel?,
    zapping: Stream?,
    recently: Boolean,
    isVodOrSeriesPlaylist: Boolean,
    onClick: (Stream) -> Unit,
    onLongClick: (Stream) -> Unit,
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

    val streams = channel?.streams?.collectAsLazyPagingItems()

    LazyVerticalStaggeredGrid(
        state = state,
        columns = StaggeredGridCells.Fixed(actualRowCount),
        verticalItemSpacing = spacing.medium,
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        contentPadding = PaddingValues(spacing.medium) + contentPadding,
        modifier = modifier.fillMaxSize()
    ) {
        items(
            count = streams?.itemCount ?: 0,
            key = { it }
        ) { index ->
            val stream = streams?.get(index)
            if (stream != null) {
                SmartphoneStreamItem(
                    stream = stream,
                    getProgrammeCurrently = { getProgrammeCurrently(stream.channelId.orEmpty()) },
                    recently = recently,
                    zapping = zapping == stream,
                    isVodOrSeriesPlaylist = isVodOrSeriesPlaylist,
                    onClick = { onClick(stream) },
                    onLongClick = { onLongClick(stream) },
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
}
