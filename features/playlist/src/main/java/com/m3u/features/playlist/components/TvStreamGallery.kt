package com.m3u.features.playlist.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.Dp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.m3u.data.database.model.Stream
import com.m3u.features.playlist.PlaylistViewModel
import com.m3u.material.model.LocalSpacing

@Composable
internal fun TvStreamGallery(
    channels: List<PlaylistViewModel.Channel>,
    maxBrowserHeight: Dp,
    isSpecifiedSort: Boolean,
    isVodOrSeriesPlaylist: Boolean,
    onClick: (Stream) -> Unit,
    onLongClick: (Stream) -> Unit,
    onFocus: (Stream) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val multiCategories = channels.size > 1

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
        contentPadding = PaddingValues(vertical = spacing.medium),
        modifier = Modifier
            .heightIn(max = maxBrowserHeight)
            .fillMaxWidth()
            .then(modifier)
    ) {
        items(channels) { (category, flow) ->
            val streams = flow.collectAsLazyPagingItems()
            if (multiCategories && streams.itemCount > 0) {
                Text(
                    text = category,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(spacing.medium)
                )
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                contentPadding = PaddingValues(horizontal = spacing.medium),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(streams.itemCount) { index ->
                    val stream = streams[index]
                    if (stream != null) {
                        TvStreamItem(
                            stream = stream,
                            isVodOrSeriesPlaylist = isVodOrSeriesPlaylist,
                            isGridLayout = false,
                            onClick = { onClick(stream) },
                            onLongClick = { onLongClick(stream) },
                            modifier = Modifier.onFocusChanged {
                                if (it.hasFocus) {
                                    onFocus(stream)
                                }
                            }
                        )
                    } else {
                        // TODO: placeholder
                    }
                }
            }
        }
    }
}
