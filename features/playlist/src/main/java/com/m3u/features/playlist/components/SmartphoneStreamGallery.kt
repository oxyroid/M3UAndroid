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
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.m3u.core.architecture.preferences.LocalPreferences
import com.m3u.data.database.model.Stream
import com.m3u.data.database.model.StreamWithProgramme
import com.m3u.material.components.CircularProgressIndicator
import com.m3u.material.ktx.plus
import com.m3u.material.model.LocalSpacing

@Composable
internal fun SmartphoneStreamGallery(
    state: LazyStaggeredGridState,
    rowCount: Int,
    streams: List<Stream>,
    streamPaged: LazyPagingItems<StreamWithProgramme>,
    zapping: Stream?,
    recently: Boolean,
    isVodOrSeriesPlaylist: Boolean,
    onClick: (Stream) -> Unit,
    onLongClick: (Stream) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    SmartphoneStreamGalleryImpl(
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

@Composable
private fun SmartphoneStreamGalleryImpl(
    state: LazyStaggeredGridState,
    rowCount: Int,
    streams: List<Stream>,
    streamPaged: LazyPagingItems<StreamWithProgramme>,
    zapping: Stream?,
    recently: Boolean,
    isVodOrSeriesPlaylist: Boolean,
    onClick: (Stream) -> Unit,
    onLongClick: (Stream) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val spacing = LocalSpacing.current
    val preferences = LocalPreferences.current

    val actualRowCount = when {
        preferences.noPictureMode -> rowCount
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
        if (!preferences.paging) {
            items(
                items = streams,
                key = { stream -> stream.id },
                contentType = { it.cover.isNullOrEmpty() }
            ) { stream ->
                SmartphoneStreamItem(
                    // todo
                    withProgramme = StreamWithProgramme(stream),
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
                val withProgramme = streamPaged[it]
                if (withProgramme != null) {
                    SmartphoneStreamItem(
                        withProgramme = withProgramme,
                        recently = recently,
                        zapping = zapping == withProgramme.stream,
                        isVodOrSeriesPlaylist = isVodOrSeriesPlaylist,
                        onClick = { onClick(withProgramme.stream) },
                        onLongClick = { onLongClick(withProgramme.stream) },
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
}
