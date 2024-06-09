package com.m3u.feature.playlist.components

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
import com.m3u.data.database.model.Channel
import com.m3u.feature.playlist.PlaylistViewModel
import com.m3u.material.model.LocalSpacing

@Composable
internal fun TvChannelGallery(
    categoryWithChannels: List<PlaylistViewModel.CategoryWithChannels>,
    maxBrowserHeight: Dp,
    isSpecifiedSort: Boolean,
    isVodOrSeriesPlaylist: Boolean,
    onClick: (Channel) -> Unit,
    onLongClick: (Channel) -> Unit,
    onFocus: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val multiCategories = categoryWithChannels.size > 1

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
        contentPadding = PaddingValues(vertical = spacing.medium),
        modifier = Modifier
            .heightIn(max = maxBrowserHeight)
            .fillMaxWidth()
            .then(modifier)
    ) {
        items(categoryWithChannels) { (category, flow) ->
            val channels = flow.collectAsLazyPagingItems()
            if (multiCategories && channels.itemCount > 0) {
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
                items(channels.itemCount) { index ->
                    val channel = channels[index]
                    if (channel != null) {
                        TvChannelItem(
                            channel = channel,
                            isVodOrSeriesPlaylist = isVodOrSeriesPlaylist,
                            isGridLayout = false,
                            onClick = { onClick(channel) },
                            onLongClick = { onLongClick(channel) },
                            modifier = Modifier.onFocusChanged {
                                if (it.hasFocus) {
                                    onFocus(channel)
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
