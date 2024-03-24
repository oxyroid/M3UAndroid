package com.m3u.features.playlist.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.data.database.model.Stream
import com.m3u.material.ktx.plus
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.UiMode
import com.m3u.ui.currentUiMode
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun StreamGallery(
    state: LazyStaggeredGridState,
    rowCount: Int,
    streams: ImmutableList<Stream>,
    streamPaged: LazyPagingItems<Stream>,
    zapping: Stream?,
    recently: Boolean,
    isVodOrSeriesPlaylist: Boolean,
    onClick: (Stream) -> Unit,
    onLongClick: (Stream) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    when (currentUiMode()) {
        UiMode.Default -> {
            StreamGalleryImpl(
                state = state,
                rowCount = rowCount,
                streams = streams,
                streamPaged = streamPaged,
                zapping = zapping,
                recently = recently,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = modifier,
                contentPadding = contentPadding,
                isVodOrSeriesPlaylist = isVodOrSeriesPlaylist
            )
        }

        UiMode.Compat -> {
            CompactStreamGalleryImpl(
                state = state,
                rowCount = rowCount,
                streams = streams,
                streamPaged = streamPaged,
                zapping = zapping,
                recently = recently,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = modifier,
                contentPadding = contentPadding
            )
        }

        else -> {}
    }
}

@Composable
private fun StreamGalleryImpl(
    state: LazyStaggeredGridState,
    rowCount: Int,
    streams: ImmutableList<Stream>,
    streamPaged: LazyPagingItems<Stream>,
    zapping: Stream?,
    recently: Boolean,
    isVodOrSeriesPlaylist: Boolean,
    onClick: (Stream) -> Unit,
    onLongClick: (Stream) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val spacing = LocalSpacing.current
    val pref = LocalPref.current

    val actualRowCount = when {
        pref.noPictureMode -> rowCount
        isVodOrSeriesPlaylist -> rowCount + 2
        else -> rowCount
    }

    LazyVerticalStaggeredGrid(
        state = state,
        columns = StaggeredGridCells.Fixed(actualRowCount),
        verticalItemSpacing = spacing.medium,
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        contentPadding = PaddingValues(spacing.medium) + contentPadding,
        modifier = modifier.fillMaxSize()
    ) {
        if (!pref.paging) {
            items(
                items = streams,
                key = { stream -> stream.id },
                contentType = { it.cover.isNullOrEmpty() }
            ) { stream ->
                StreamItem(
                    stream = stream,
                    recently = recently,
                    zapping = zapping == stream,
                    isVodOrSeriesPlaylist = isVodOrSeriesPlaylist,
                    onClick = { onClick(stream) },
                    onLongClick = { onLongClick(stream) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            items(streamPaged.itemCount) {
                streamPaged[it]?.let { stream ->
                    StreamItem(
                        stream = stream,
                        recently = recently,
                        zapping = zapping == stream,
                        isVodOrSeriesPlaylist = isVodOrSeriesPlaylist,
                        onClick = { onClick(stream) },
                        onLongClick = { onLongClick(stream) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactStreamGalleryImpl(
    state: LazyStaggeredGridState,
    rowCount: Int,
    streams: ImmutableList<Stream>,
    streamPaged: LazyPagingItems<Stream>,
    zapping: Stream?,
    recently: Boolean,
    onClick: (Stream) -> Unit,
    onLongClick: (Stream) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val pref = LocalPref.current
    LazyVerticalStaggeredGrid(
        state = state,
        columns = StaggeredGridCells.Fixed(rowCount),
        contentPadding = contentPadding,
        modifier = modifier.fillMaxSize()
    ) {
        if (!pref.paging) {
            items(
                items = streams,
                key = { stream -> stream.id },
                contentType = { it.cover.isNullOrEmpty() }
            ) { stream ->
                StreamItem(
                    stream = stream,
                    recently = recently,
                    zapping = zapping == stream,
                    onClick = { onClick(stream) },
                    onLongClick = { onLongClick(stream) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            items(streamPaged.itemCount) {
                streamPaged[it]?.let { stream ->
                    StreamItem(
                        stream = stream,
                        recently = recently,
                        zapping = zapping == stream,
                        onClick = { onClick(stream) },
                        onLongClick = { onLongClick(stream) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
