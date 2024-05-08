package com.m3u.features.playlist.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.Dp
import androidx.paging.compose.LazyPagingItems
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.data.database.model.Stream
import com.m3u.features.playlist.Category
import com.m3u.material.components.CircularProgressIndicator
import com.m3u.material.model.LocalSpacing

@Composable
internal fun TvStreamGallery(
    categories: List<Category>,
    streamPaged: LazyPagingItems<Stream>,
    maxBrowserHeight: Dp,
    useGridLayout: Boolean,
    isVodOrSeriesPlaylist: Boolean,
    onClick: (Stream) -> Unit,
    onLongClick: (Stream) -> Unit,
    onFocus: (Stream) -> Unit,
    modifier: Modifier = Modifier,
) {
    val preferences = hiltPreferences()
    val spacing = LocalSpacing.current
    val multiCategories = categories.size > 1

    val paging = preferences.paging

    if (!useGridLayout) {
        TvLazyColumn(
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
            contentPadding = PaddingValues(vertical = spacing.medium),
            modifier = Modifier
                .heightIn(max = maxBrowserHeight)
                .fillMaxWidth()
                .then(modifier)
        ) {
            items(categories) { channel ->
                if (multiCategories) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(spacing.medium)
                    )
                }
                val streams = channel.streams
                TvLazyRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                    contentPadding = PaddingValues(horizontal = spacing.medium),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(
                        items = streams,
                        key = { stream -> stream.id },
                    ) { stream ->
                        TvStreamItem(
                            stream = stream,
                            isVodOrSeriesPlaylist = isVodOrSeriesPlaylist,
                            onClick = { onClick(stream) },
                            onLongClick = { onLongClick(stream) },
                            modifier = Modifier.onFocusChanged {
                                if (it.hasFocus) {
                                    onFocus(stream)
                                }
                            }
                        )
                    }
                }
            }
        }
    } else {
        val actualRowCount = if (isVodOrSeriesPlaylist) preferences.rowCount + 7
        else preferences.rowCount + 5
        TvLazyVerticalGrid(
            columns = TvGridCells.Fixed(actualRowCount),
            contentPadding = PaddingValues(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(spacing.large),
            modifier = Modifier
                .heightIn(max = maxBrowserHeight)
                .fillMaxWidth()
        ) {
            if (!paging) {
                categories.forEach { category ->
                    items(category.streams) { stream ->
                        TvStreamItem(
                            stream = stream,
                            isVodOrSeriesPlaylist = isVodOrSeriesPlaylist,
                            onClick = { onClick(stream) },
                            onLongClick = { onLongClick(stream) },
                            modifier = Modifier
                                .onFocusChanged {
                                    if (it.hasFocus) {
                                        onFocus(stream)
                                    }
                                }
                        )
                    }
                }
            } else {
                items(streamPaged.itemCount) {
                    val stream = streamPaged[it]
                    if (stream != null) {
                        TvStreamItem(
                            stream = stream,
                            isVodOrSeriesPlaylist = isVodOrSeriesPlaylist,
                            onClick = { onClick(stream) },
                            onLongClick = { onLongClick(stream) },
                            modifier = Modifier
                                .onFocusChanged { focusState ->
                                    if (focusState.hasFocus) {
                                        onFocus(stream)
                                    }
                                }
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(1f)
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}
