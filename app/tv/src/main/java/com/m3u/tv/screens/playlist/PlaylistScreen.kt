package com.m3u.tv.screens.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.PagingData
import androidx.tv.material3.MaterialTheme
import com.m3u.business.playlist.PlaylistViewModel
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.type
import kotlinx.coroutines.flow.Flow

@Composable
fun PlaylistScreen(
    onChannelClick: (channel: Channel) -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val channels: Map<String, Flow<PagingData<Channel>>> by viewModel.channels.collectAsStateWithLifecycle()
    val pinnedCategories by viewModel.pinnedCategories.collectAsStateWithLifecycle()
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()

    ThreeColumnCatalog(
        channels = channels,
        pinnedCategories = pinnedCategories,
        playlist = playlist,
        onPinOrUnpinCategory = viewModel::onPinOrUnpinCategory,
        onHideCategory = viewModel::onHideCategory,
        onChannelClick = onChannelClick,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ThreeColumnCatalog(
    channels: Map<String, Flow<PagingData<Channel>>>,
    pinnedCategories: List<String>,
    playlist: Playlist?,
    onPinOrUnpinCategory: (String) -> Unit,
    onHideCategory: (String) -> Unit,
    onChannelClick: (channel: Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedChannel by remember { mutableStateOf<Channel?>(null) }

    // Set initial category if not selected
    if (selectedCategory == null && channels.isNotEmpty()) {
        selectedCategory = channels.keys.first()
    }

    // Determine if we should show live preview (only for Live TV, not Movies/Series)
    val showLivePreview = remember(playlist) {
        val type = playlist?.type
        type == null || type == com.m3u.data.database.model.DataSource.Xtream.TYPE_LIVE
    }

    Row(
        modifier = modifier.background(MaterialTheme.colorScheme.background)
    ) {
        // Left Column: Categories
        Box(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
        ) {
            CategoryList(
                categories = channels.keys.toList(),
                pinnedCategories = pinnedCategories,
                selectedCategory = selectedCategory,
                onCategorySelected = { category ->
                    selectedCategory = category
                    selectedChannel = null // Reset channel selection when category changes
                },
                onPinOrUnpinCategory = onPinOrUnpinCategory,
                onHideCategory = onHideCategory,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Middle Column: Channels
        Box(
            modifier = Modifier
                .width(400.dp)
                .fillMaxHeight()
                .padding(start = 8.dp)
        ) {
            selectedCategory?.let { category ->
                channels[category]?.let { channelFlow ->
                    ChannelList(
                        channels = channelFlow,
                        selectedChannel = selectedChannel,
                        onChannelSelected = { channel ->
                            selectedChannel = channel
                        },
                        onChannelClick = onChannelClick,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Right Column: Live Preview + EPG (only for Live TV content)
        if (showLivePreview) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(start = 8.dp)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                selectedChannel?.let { channel ->
                    LivePreviewPanel(
                        channel = channel,
                        modifier = Modifier.fillMaxSize()
                    )
                } ?: run {
                    // Show placeholder when no channel selected
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                    ) {
                        androidx.tv.material3.Text(
                            text = "Select a channel to preview",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // Empty space for Movies/Series (no live preview)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(start = 8.dp)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // Intentionally empty - just provides spacing
            }
        }
    }
}
