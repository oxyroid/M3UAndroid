package com.m3u.tv.screens.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.m3u.data.database.model.Channel
import kotlinx.coroutines.flow.Flow

@Composable
fun ChannelList(
    channels: Flow<PagingData<Channel>>,
    selectedChannel: Channel?,
    onChannelSelected: (Channel) -> Unit,
    onChannelClick: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagingChannels = channels.collectAsLazyPagingItems()
    val listState = rememberLazyListState()

    // ENTERPRISE SMOOTH SCROLLING: Only scroll when item is near edges
    // This prevents jarring jumps and provides smooth navigation
    LaunchedEffect(selectedChannel) {
        selectedChannel?.let { selected ->
            val index = (0 until pagingChannels.itemCount).find { i ->
                pagingChannels[i]?.id == selected.id
            }

            index?.let { targetIndex ->
                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo

                if (visibleItems.isEmpty()) {
                    // List not yet laid out, do initial scroll
                    listState.scrollToItem(targetIndex)
                } else {
                    val viewportStart = layoutInfo.viewportStartOffset
                    val viewportEnd = layoutInfo.viewportEndOffset
                    val viewportSize = viewportEnd - viewportStart

                    // Define threshold zones (20% from top/bottom)
                    val scrollThreshold = (viewportSize * 0.2f).toInt()
                    val topThreshold = viewportStart + scrollThreshold
                    val bottomThreshold = viewportEnd - scrollThreshold

                    // Find if current item is visible
                    val currentItem = visibleItems.find { it.index == targetIndex }

                    if (currentItem == null) {
                        // Item not visible at all, scroll it into view
                        // Use smooth scroll to center position
                        listState.animateScrollToItem(targetIndex)
                    } else {
                        // Item is visible, check if it's in threshold zone
                        val itemTop = currentItem.offset
                        val itemBottom = currentItem.offset + currentItem.size

                        when {
                            // Item is too close to top edge, scroll up a bit
                            itemTop < topThreshold -> {
                                val itemsToScroll = ((topThreshold - itemTop) / currentItem.size) + 1
                                val scrollTargetIndex = (targetIndex - itemsToScroll).coerceAtLeast(0)
                                listState.animateScrollToItem(scrollTargetIndex)
                            }
                            // Item is too close to bottom edge, scroll down a bit
                            itemBottom > bottomThreshold -> {
                                val itemsToScroll = ((itemBottom - bottomThreshold) / currentItem.size) + 1
                                val scrollTargetIndex = (targetIndex + itemsToScroll).coerceAtMost(pagingChannels.itemCount - 1)
                                listState.animateScrollToItem(scrollTargetIndex)
                            }
                            // Item is comfortably in view, no scroll needed
                            else -> {
                                // No scrolling needed - this prevents the jump!
                            }
                        }
                    }
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(
            count = pagingChannels.itemCount,
            key = { index -> pagingChannels[index]?.id ?: index }
        ) { index ->
            val channel = pagingChannels[index]
            if (channel != null) {
                ChannelListItem(
                    channel = channel,
                    isSelected = channel.id == selectedChannel?.id,
                    onChannelSelected = { onChannelSelected(channel) },
                    onChannelClick = { onChannelClick(channel) }
                )
            }
        }
    }
}

@Composable
private fun ChannelListItem(
    channel: Channel,
    isSelected: Boolean,
    onChannelSelected: () -> Unit,
    onChannelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    // Auto-select channel when focused
    LaunchedEffect(isFocused) {
        if (isFocused) {
            onChannelSelected()
        }
    }

    ListItem(
        selected = isSelected,
        onClick = onChannelClick,
        leadingContent = {
            // Channel logo thumbnail
            AsyncImage(
                model = channel.cover,
                contentDescription = channel.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            )
        },
        headlineContent = {
            Text(
                text = channel.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected || isFocused) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            // Show category as supporting text
            Text(
                text = channel.category,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        colors = ListItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            focusedSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            containerColor = Color.Transparent,
        ),
        modifier = modifier.onFocusChanged { isFocused = it.isFocused || it.hasFocus }
    )
}
