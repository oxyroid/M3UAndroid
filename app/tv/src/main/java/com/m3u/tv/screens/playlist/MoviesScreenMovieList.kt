package com.m3u.tv.screens.playlist

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.tv.material3.Border
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CompactCard
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.m3u.business.playlist.PlaylistViewModel
import com.m3u.data.database.model.Channel
import com.m3u.tv.theme.JetStreamBorderWidth
import com.m3u.tv.utils.longPressKeyHandler

/** Item widths for playlist item size: Large, Medium, Small, Compact */
private val PLAYLIST_ITEM_WIDTHS = listOf(432.dp, 340.dp, 260.dp, 200.dp)

fun LazyListScope.channelGallery(
    channels: List<Pair<PlaylistViewModel.CategoryWithChannels, LazyPagingItems<Channel>>>,
    startPadding: Dp,
    endPadding: Dp,
    itemWidth: Dp,
    onChannelLongClick: (channel: Channel) -> Unit,
) {
    items(channels, key = { (cwc, _) -> cwc.category }) { (_, pagingChannels) ->
        LazyRow(
            modifier = Modifier.focusRestorer(),
            contentPadding = PaddingValues(start = startPadding, end = endPadding),
        ) {
            items(pagingChannels.itemCount) {
                val channel = pagingChannels[it]
                if (channel != null) {
                    ChannelGalleryItem(
                        itemWidth = itemWidth,
                        onChannelLongClick = onChannelLongClick,
                        channel = channel,
                    )
                }
            }
        }
    }
}

fun playlistItemWidthForSize(size: Int): Dp =
    PLAYLIST_ITEM_WIDTHS[(size).coerceIn(0, PLAYLIST_ITEM_WIDTHS.lastIndex)]

/** Single row of channels (e.g. for Favorites) using the same card as playlist gallery. */
fun LazyListScope.favouriteChannelGallery(
    channels: List<Channel>,
    startPadding: Dp,
    endPadding: Dp,
    itemWidth: Dp,
    onChannelLongClick: (channel: Channel) -> Unit,
) {
    item {
        LazyRow(
            modifier = Modifier.focusRestorer(),
            contentPadding = PaddingValues(start = startPadding, end = endPadding),
        ) {
            items(channels, key = { it.id }) { channel ->
                ChannelGalleryItem(
                    itemWidth = itemWidth,
                    onChannelLongClick = onChannelLongClick,
                    channel = channel,
                )
            }
        }
    }
}

@Composable
internal fun ChannelGalleryItem(
    itemWidth: Dp,
    channel: Channel,
    modifier: Modifier = Modifier,
    onChannelLongClick: (channel: Channel) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(JetStreamBorderWidth))
        var isFocused by remember { mutableStateOf(false) }
        // longPressKeyHandler MUST live on a parent Box, not on CompactCard itself.
        // onPreviewKeyEvent intercepts events before *children* — but CompactCard's internal
        // Clickable fires its onClick via performClick() on ACTION_UP, which bypasses the key
        // event pipeline when the modifier is on the card's own node. A parent Box's
        // onPreviewKeyEvent runs before the event ever reaches the card subtree.
        Box(
            modifier = modifier
                .width(itemWidth)
                .aspectRatio(2f)
                .padding(end = 32.dp)
                .longPressKeyHandler(
                    onClick = { },
                    onLongClick = { onChannelLongClick(channel) },
                )
        ) {
            CompactCard(
                modifier = Modifier
                    .fillMaxSize()
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
                // No-op: parent Box's longPressKeyHandler consumes all center/enter events
                // before they reach here. Non-null onClick keeps the card focusable.
                onClick = {},
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
}
