package com.m3u.tv.screens.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.m3u.business.foryou.ForyouViewModel
import com.m3u.business.playlist.PlaylistViewModel
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.type
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlaylistManagementCard(
    playlist: Playlist,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .width(280.dp)
            .height(120.dp),
        colors = CardDefaults.colors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            contentColor = if (isSelected)
                MaterialTheme.colorScheme.onPrimaryContainer
            else
                Color.White
        ),
        shape = CardDefaults.shape(MaterialTheme.shapes.medium),
        border = CardDefaults.border(
            focusedBorder = Border(
                BorderStroke(4.dp, Color.White),
                shape = MaterialTheme.shapes.medium
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Show type for Xtream playlists
            val typeText = when {
                playlist.source == DataSource.Xtream && playlist.type != null -> {
                    when (playlist.type) {
                        DataSource.Xtream.TYPE_LIVE -> "XTREAM • LIVE TV"
                        DataSource.Xtream.TYPE_VOD -> "XTREAM • MOVIES"
                        DataSource.Xtream.TYPE_SERIES -> "XTREAM • SERIES"
                        else -> playlist.source.value.uppercase()
                    }
                }
                else -> playlist.source.value.uppercase()
            }

            Text(
                text = typeText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlaylistManagementOptionsSimplified(
    playlist: Playlist,
    channelCount: Int,
    foryouViewModel: ForyouViewModel,
    onPlaylistDeleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with delete button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Managing: ${playlist.title}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Total channels: $channelCount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Delete playlist button
            if (!showDeleteConfirmation) {
                Button(
                    onClick = { showDeleteConfirmation = true },
                    colors = ButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Delete playlist"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("DELETE PLAYLIST")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                foryouViewModel.onUnsubscribePlaylist(playlist.url)
                                showDeleteConfirmation = false
                                onPlaylistDeleted()
                            }
                        },
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("CONFIRM DELETE")
                    }
                    Button(onClick = { showDeleteConfirmation = false }) {
                        Text("CANCEL")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3-Column Management Interface
        Text(
            text = "Playlist management: Navigate to the playlist in FOR YOU to manage groups and channels.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FullPlaylistManagementView(
    playlist: Playlist,
    channelCount: Int,
    playlistUrl: String,
    foryouViewModel: ForyouViewModel,
    playlistViewModel: PlaylistViewModel,
    onPlaylistDeleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedChannel by remember { mutableStateOf<Channel?>(null) }

    // Get ALL channels for this playlist (including those in hidden categories)
    val allChannels by playlistViewModel.getAllChannelsForPlaylist(playlistUrl)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // Group channels by category (includes ALL categories, even hidden ones)
    val allCategoriesMap = remember(allChannels) {
        allChannels
            .groupBy { it.category }
            .toSortedMap()  // Sort alphabetically
    }

    val allCategories = remember(allCategoriesMap) {
        allCategoriesMap.keys.toList()
    }

    // Get hidden and pinned categories from playlist
    val hiddenCategories = playlist.hiddenCategories
    val pinnedCategories = playlist.pinnedCategories

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with delete button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Managing: ${playlist.title}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Total channels: $channelCount • Press SELECT on group to hide/unhide",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!showDeleteConfirmation) {
                Button(
                    onClick = { showDeleteConfirmation = true },
                    colors = ButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(Icons.Rounded.Delete, "Delete")
                    Spacer(Modifier.width(8.dp))
                    Text("DELETE")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                foryouViewModel.onUnsubscribePlaylist(playlist.url)
                                showDeleteConfirmation = false
                                onPlaylistDeleted()
                            }
                        },
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("CONFIRM")
                    }
                    Button(onClick = { showDeleteConfirmation = false }) {
                        Text("CANCEL")
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Legend
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            LegendItem("🔴 Red = Hidden", Color.Red)
            LegendItem("🟢 Green = Favorite", Color.Green)
            LegendItem("⚪ No color = Normal", Color.White)
        }

        Spacer(Modifier.height(8.dp))

        // 3-Column Management View
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp),  // Fixed height to ensure visibility
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Column 1: Groups/Categories (ALL including hidden)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        MaterialTheme.shapes.medium
                    )
            ) {
                Text(
                    text = "GROUPS (${allCategories.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    modifier = Modifier.padding(16.dp)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(allCategories, key = { it }) { category ->
                        ManagementCategoryItem(
                            category = category,
                            isHidden = category in hiddenCategories,
                            isPinned = category in pinnedCategories,
                            isSelected = category == selectedCategory,
                            onSelected = {
                                selectedCategory = category
                                // Auto-select first channel when category changes
                                selectedChannel = allCategoriesMap[category]?.firstOrNull()
                            },
                            onToggleState = {
                                // 3-state cycle: Normal -> Hidden -> Favorite -> Normal
                                val isCurrentlyHidden = category in hiddenCategories
                                val isCurrentlyPinned = category in pinnedCategories

                                when {
                                    // State 1: Normal -> Hidden
                                    !isCurrentlyHidden && !isCurrentlyPinned -> {
                                        playlistViewModel.onHideCategory(playlistUrl, category)
                                    }
                                    // State 2: Hidden -> Favorite (unhide and pin)
                                    isCurrentlyHidden && !isCurrentlyPinned -> {
                                        playlistViewModel.onHideCategory(playlistUrl, category) // Unhide
                                        playlistViewModel.onPinOrUnpinCategory(playlistUrl, category) // Pin
                                    }
                                    // State 3: Favorite -> Normal (unpin)
                                    !isCurrentlyHidden && isCurrentlyPinned -> {
                                        playlistViewModel.onPinOrUnpinCategory(playlistUrl, category) // Unpin
                                    }
                                    // Edge case: Hidden AND Pinned -> Normal (shouldn't happen but handle it)
                                    else -> {
                                        if (isCurrentlyHidden) {
                                            playlistViewModel.onHideCategory(playlistUrl, category)
                                        }
                                        if (isCurrentlyPinned) {
                                            playlistViewModel.onPinOrUnpinCategory(playlistUrl, category)
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // Column 2: Channels in selected group
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        MaterialTheme.shapes.medium
                    )
            ) {
                val channelsInCategory = selectedCategory?.let { allCategoriesMap[it] } ?: emptyList()
                Text(
                    text = "CHANNELS (${channelsInCategory.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    modifier = Modifier.padding(16.dp)
                )
                if (selectedCategory == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Select a group to view channels",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(channelsInCategory, key = { it.id }) { channel ->
                            ManagementChannelItem(
                                channel = channel,
                                isSelected = channel.id == selectedChannel?.id,
                                onSelected = { selectedChannel = channel }
                            )
                        }
                    }
                }
            }

            // Column 3: Preview of selected channel
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        MaterialTheme.shapes.medium
                    )
            ) {
                Text(
                    text = "PREVIEW",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    modifier = Modifier.padding(16.dp)
                )
                if (selectedChannel == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Select a channel to preview",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    ChannelPreview(channel = selectedChannel!!)
                }
            }
        }
    }
}

@Composable
private fun LegendItem(text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(color.copy(alpha = 0.3f), MaterialTheme.shapes.small)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ManagementCategoryItem(
    category: String,
    isHidden: Boolean,
    isPinned: Boolean,
    isSelected: Boolean,
    onSelected: () -> Unit,
    onToggleState: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    // Auto-select on focus
    LaunchedEffect(isFocused) {
        if (isFocused) {
            onSelected()
        }
    }

    // Determine container colors based on state
    val containerColor = when {
        isHidden -> Color.Red.copy(alpha = 0.4f)
        isPinned -> Color.Green.copy(alpha = 0.4f)
        else -> Color.Transparent
    }

    val selectedContainerColor = when {
        isHidden -> Color.Red.copy(alpha = 0.5f)
        isPinned -> Color.Green.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    }

    val focusedSelectedContainerColor = when {
        isHidden -> Color.Red.copy(alpha = 0.6f)
        isPinned -> Color.Green.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    ListItem(
        selected = isSelected,
        onClick = {
            onSelected()
            // Cycle through states on SELECT press
            onToggleState()
        },
        headlineContent = {
            Text(
                text = category,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = containerColor,
            selectedContainerColor = selectedContainerColor,
            focusedSelectedContainerColor = focusedSelectedContainerColor,
        ),
        modifier = modifier.onFocusChanged { isFocused = it.isFocused || it.hasFocus }
    )
}

@Composable
private fun ManagementChannelItem(
    channel: Channel,
    isSelected: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    // Auto-select on focus
    LaunchedEffect(isFocused) {
        if (isFocused) {
            onSelected()
        }
    }

    ListItem(
        selected = isSelected,
        onClick = onSelected,
        headlineContent = {
            Text(
                text = channel.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (channel.hidden) Color.Red else Color.White,
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

@Composable
private fun ChannelPreview(
    channel: Channel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Channel title
        Text(
            text = channel.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // Channel info
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PreviewInfoRow(label = "Category", value = channel.category)
            PreviewInfoRow(label = "URL", value = channel.url, maxLines = 2)
            if (channel.cover != null) {
                PreviewInfoRow(label = "Cover", value = "Available")
            }
            PreviewInfoRow(
                label = "Status",
                value = when {
                    channel.hidden -> "Hidden"
                    channel.favourite -> "Favorite"
                    else -> "Normal"
                },
                valueColor = when {
                    channel.hidden -> Color.Red
                    channel.favourite -> Color.Green
                    else -> Color.White
                }
            )
        }
    }
}

@Composable
private fun PreviewInfoRow(
    label: String,
    value: String,
    valueColor: Color = Color.White,
    maxLines: Int = 1,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}
