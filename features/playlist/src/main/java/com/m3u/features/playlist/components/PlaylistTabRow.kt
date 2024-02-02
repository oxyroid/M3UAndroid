package com.m3u.features.playlist.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.m3u.features.playlist.Channel
import com.m3u.material.components.IconButton
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun PlaylistTabRow(
    page: Int,
    onPageChanged: (Int) -> Unit,
    channels: ImmutableList<Channel>,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        if (channels.size > 1) {
            Column {
                val state = rememberLazyListState()
                LazyRow(
                    state = state,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    stickyHeader {
                        IconButton(
                            icon = Icons.Rounded.Menu,
                            contentDescription = "",
                            onClick = { /*TODO*/ }
                        )
                    }
                    itemsIndexed(channels) { index, channel ->
                        val selected = page == index
                        Tab(
                            selected = selected,
                            onClick = { onPageChanged(index) },
                            text = {
                                Text(
                                    text = channel.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (selected) MaterialTheme.colorScheme.onBackground
                                    else Color.Unspecified,
                                    fontWeight = FontWeight.Bold.takeIf { selected }
                                )
                            }
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
