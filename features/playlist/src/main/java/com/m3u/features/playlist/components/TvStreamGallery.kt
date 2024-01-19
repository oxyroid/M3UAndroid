package com.m3u.features.playlist.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.Dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.m3u.data.database.model.Stream
import com.m3u.features.playlist.Channel
import com.m3u.material.model.LocalSpacing
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun TvStreamGallery(
    channels: ImmutableList<Channel>,
    maxBrowserHeight: Dp,
    onClick: (Stream, i: Int, j: Int) -> Unit,
    onLongClick: (Stream, i: Int, j: Int) -> Unit,
    onFocus: (Stream, i: Int, j: Int) -> Unit,
    modifier: Modifier = Modifier,
    noPictureMode: Boolean = false
) {
    val spacing = LocalSpacing.current
    val multiCatalogs = channels.size > 1

    TvLazyColumn(
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
        contentPadding = PaddingValues(vertical = spacing.medium),
        modifier = Modifier
            .heightIn(max = maxBrowserHeight)
            .then(modifier)
    ) {
        itemsIndexed(channels) { i, channel ->
            if (multiCatalogs) {
                Text(
                    text = channel.title,
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
                itemsIndexed(
                    items = streams,
                    key = { _, stream -> stream.id },
                    contentType = { _, stream -> stream.cover.isNullOrEmpty() }
                ) { j, stream ->
                    TvStreamItem(
                        stream = stream,
                        noPictureMode = noPictureMode,
                        onClick = {
                            onClick(stream, i, j)
                        },
                        onLongClick = {
                            onLongClick(stream, i, j)
                        },
                        modifier = Modifier
                            .onFocusChanged {
                                if (it.hasFocus) {
                                    onFocus(stream, i, j)
                                }
                            }
                        //.immersiveListItem(mixed)
                    )
                }
            }
        }
    }
}
