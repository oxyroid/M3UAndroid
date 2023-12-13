package com.m3u.features.playlist.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyGridState
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.data.database.entity.Stream
import com.m3u.data.database.entity.StreamHolder
import com.m3u.material.ktx.plus
import com.m3u.material.model.LocalSpacing
import dev.chrisbanes.haze.haze

typealias PlayStream = (url: String) -> Unit

@Composable
internal fun StreamGallery(
    state: LazyStaggeredGridState,
    rowCount: Int,
    streamHolder: StreamHolder,
    play: PlayStream,
    onMenu: (Stream) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val spacing = LocalSpacing.current
    val pref = LocalPref.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    val streams = streamHolder.streams
    val floating = streamHolder.floating

    LazyVerticalStaggeredGrid(
        state = state,
        columns = StaggeredGridCells.Fixed(rowCount),
        verticalItemSpacing = spacing.medium,
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        contentPadding = PaddingValues(spacing.medium) + contentPadding,
        modifier = modifier
            .fillMaxSize()
            .haze(
                area = with(density) {
                    arrayOf(
                        Rect(
                            0f,
                            0f,
                            configuration.screenWidthDp.dp.toPx(),
                            contentPadding.calculateTopPadding().toPx()
                        )
                    )
                },
                backgroundColor = MaterialTheme.colorScheme.background,
                blurRadius = 2.dp
            )
    ) {
        items(
            items = streams,
            key = { stream -> stream.id },
            contentType = { it.cover.isNullOrEmpty() }
        ) { stream ->
            StreamItem(
                stream = stream,
                border = floating != stream,
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
internal fun TvStreamGallery(
    state: TvLazyGridState,
    rowCount: Int,
    streamHolder: StreamHolder,
    play: PlayStream,
    onMenu: (Stream) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val spacing = LocalSpacing.current
    val pref = LocalPref.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    val streams = streamHolder.streams
    val floating = streamHolder.floating

    TvLazyVerticalGrid(
        state = state,
        columns = TvGridCells.Fixed(rowCount),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        contentPadding = PaddingValues(spacing.medium) + contentPadding,
        modifier = modifier
            .fillMaxSize()
            .haze(
                area = with(density) {
                    arrayOf(
                        Rect(
                            0f,
                            0f,
                            configuration.screenWidthDp.dp.toPx(),
                            contentPadding.calculateTopPadding().toPx()
                        )
                    )
                },
                backgroundColor = MaterialTheme.colorScheme.background,
                blurRadius = 2.dp
            )
    ) {
        items(
            items = streams,
            key = { stream -> stream.id },
            contentType = { it.cover.isNullOrEmpty() }
        ) { stream ->
            StreamItem(
                stream = stream,
                border = floating != stream,
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