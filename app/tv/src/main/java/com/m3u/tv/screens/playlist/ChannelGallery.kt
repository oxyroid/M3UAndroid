package com.m3u.tv.screens.playlist

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.Border
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CompactCard
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.m3u.business.playlist.PlaylistViewModel
import com.m3u.core.foundation.ui.thenIf
import com.m3u.data.database.model.Channel
import com.m3u.tv.theme.JetStreamBorderWidth
import com.m3u.tv.utils.focusOnInitialVisibility

fun LazyListScope.channelGallery(
    channels: List<PlaylistViewModel.CategoryWithChannels>,
    startPadding: Dp,
    endPadding: Dp,
    onChannelClick: (channel: Channel) -> Unit
) {
    itemsIndexed(channels) { i, (category, channels) ->
        val pagingChannels = channels.collectAsLazyPagingItems()
        LazyRow(
            modifier = Modifier.focusRestorer(),
            contentPadding = PaddingValues(start = startPadding, end = endPadding),
        ) {
            items(pagingChannels.itemCount) { j ->
                val channel = pagingChannels[j]
                if (channel != null) {
                    ChannelGalleryItem(
                        itemWidth = 432.dp,
                        onChannelClick = onChannelClick,
                        channel = channel
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelGalleryItem(
    itemWidth: Dp,
    channel: Channel,
    modifier: Modifier = Modifier,
    onChannelClick: (channel: Channel) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(JetStreamBorderWidth))
        var isFocused by remember { mutableStateOf(false) }
        CompactCard(
            modifier = modifier
                .width(itemWidth)
                .aspectRatio(2f)
                .padding(end = 32.dp)
                .onFocusChanged { isFocused = it.isFocused || it.hasFocus },
            scale = CardDefaults.scale(focusedScale = 1f),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(
                        width = JetStreamBorderWidth, color = MaterialTheme.colorScheme.onSurface
                    )
                )
            ),
            colors = CardDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            onClick = { onChannelClick(channel) },
            image = {
                val contentAlpha by animateFloatAsState(
                    targetValue = if (isFocused) 1f else 0.5f,
                    label = "",
                )
                AsyncImage(
                    model = channel.cover,
                    contentDescription = channel.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = contentAlpha }
                )
            },
            title = {
                Column {
                    Text(
                        text = channel.category,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Normal
                        ),
                        modifier = Modifier
                            .graphicsLayer { alpha = 0.6f }
                            .padding(start = 24.dp)
                    )
                    Text(
                        text = channel.title,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(
                            start = 24.dp,
                            end = 24.dp,
                            bottom = 24.dp
                        ),
                        // TODO: Remove this when CardContent is not overriding contentColor anymore
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        )
    }
}
