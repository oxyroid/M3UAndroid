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
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.data.database.model.Stream
import com.m3u.material.ktx.plus
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.Sort
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun StreamGallery(
    state: LazyStaggeredGridState,
    rowCount: Int,
    streams: ImmutableList<Stream>,
    zapping: Stream?,
    sort: Sort,
    play: (url: String) -> Unit,
    onMenu: (Stream) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val pref = LocalPref.current

    if (!pref.compact) {
        StreamGalleryImpl(
            state = state,
            rowCount = rowCount,
            streams = streams,
            zapping = zapping,
            sort = sort,
            play = play,
            onMenu = onMenu,
            modifier = modifier,
            contentPadding = contentPadding
        )
    } else {
        CompactStreamGalleryImpl(
            state = state,
            rowCount = rowCount,
            streams = streams,
            zapping = zapping,
            sort = sort,
            play = play,
            onMenu = onMenu,
            modifier = modifier,
            contentPadding = contentPadding
        )
    }
}

@Composable
private fun CompactStreamGalleryImpl(
    state: LazyStaggeredGridState,
    rowCount: Int,
    streams: ImmutableList<Stream>,
    zapping: Stream?,
    sort: Sort,
    play: (url: String) -> Unit,
    onMenu: (Stream) -> Unit,
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
        items(
            items = streams,
            key = { stream -> stream.id },
            contentType = { it.cover.isNullOrEmpty() }
        ) { stream ->
            StreamItem(
                stream = stream,
                zapping = zapping == stream,
                sort = sort,
                noPictureMode = pref.noPictureMode,
                onClick = {
                    play(stream.url)
                },
                onLongClick = { onMenu(stream) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StreamGalleryImpl(
    state: LazyStaggeredGridState,
    rowCount: Int,
    streams: ImmutableList<Stream>,
    zapping: Stream?,
    sort: Sort,
    play: (url: String) -> Unit,
    onMenu: (Stream) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val spacing = LocalSpacing.current
    val pref = LocalPref.current

    LazyVerticalStaggeredGrid(
        state = state,
        columns = StaggeredGridCells.Fixed(rowCount),
        verticalItemSpacing = spacing.medium,
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        contentPadding = PaddingValues(spacing.medium) + contentPadding,
        modifier = modifier.fillMaxSize()
    ) {
        items(
            items = streams,
            key = { stream -> stream.id },
            contentType = { it.cover.isNullOrEmpty() }
        ) { stream ->
            StreamItem(
                stream = stream,
                zapping = zapping == stream,
                noPictureMode = pref.noPictureMode,
                sort = sort,
                onClick = {
                    play(stream.url)
                },
                onLongClick = { onMenu(stream) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
