package com.m3u.smartphone.ui.business.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.business.search.GlobalSearchState
import com.m3u.business.search.GlobalSearchViewModel
import com.m3u.core.util.basic.title
import com.m3u.data.database.model.Channel
import com.m3u.data.service.MediaCommand
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.common.helper.LocalHelper
import com.m3u.smartphone.ui.common.helper.Metadata
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MAX_ITEMS = 5

@Composable
fun GlobalSearchRoute(
    navigateToChannel: () -> Unit,
    navigateToPlaylist: (playlistUrl: String, category: String) -> Unit,
    searchQuery: String,
    onCollapseSearch: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: GlobalSearchViewModel = hiltViewModel()
) {
    val helper = LocalHelper.current
    val coroutineScope = rememberCoroutineScope()

    val title = stringResource(string.ui_destination_search)

    val state by viewModel.state.collectAsStateWithLifecycle()

    // Feed the universal search bar query into the ViewModel
    LaunchedEffect(searchQuery) {
        viewModel.onQueryChange(searchQuery)
    }

    LifecycleResumeEffect(title) {
        Metadata.title = AnnotatedString(title.title())
        Metadata.color = Color.Unspecified
        Metadata.contentColor = Color.Unspecified
        Metadata.actions = emptyList()
        onPauseOrDispose {}
    }

    GlobalSearchScreen(
        query = searchQuery,
        state = state,
        onChannelClick = { channel ->
            coroutineScope.launch {
                helper.play(MediaCommand.Common(channel.id))
                navigateToChannel()
                onCollapseSearch()
            }
        },
        onCategoryClick = { category ->
            coroutineScope.launch {
                val playlistUrl = viewModel.findPlaylistUrlForCategory(category)
                if (playlistUrl != null) {
                    navigateToPlaylist(playlistUrl, category)
                    onCollapseSearch()
                }
            }
        },
        contentPadding = contentPadding,
        modifier = modifier
    )
}

@Composable
private fun GlobalSearchScreen(
    query: String,
    state: GlobalSearchState,
    onChannelClick: (Channel) -> Unit,
    onCategoryClick: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        if (query.length < 3) {
            Text(
                text = "Type at least 3 characters to search",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else if (state.isEmpty) {
            Text(
                text = "No results found",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.categories.isNotEmpty()) {
                    item(key = "categories_section") {
                        ExpandableSection(
                            title = "Categories",
                            totalCount = state.categories.size
                        ) { expanded ->
                            val visible = if (expanded) state.categories else state.categories.take(MAX_ITEMS)
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                items(visible) { category ->
                                    FilterChip(
                                        selected = false,
                                        onClick = { onCategoryClick(category) },
                                        label = { Text(category) }
                                    )
                                }
                            }
                        }
                    }
                }
                if (state.channels.isNotEmpty()) {
                    item(key = "channels_section") {
                        ExpandableSection(
                            title = "Channels",
                            totalCount = state.channels.size
                        ) { expanded ->
                            val visible = if (expanded) state.channels else state.channels.take(MAX_ITEMS)
                            Column {
                                visible.forEach { channel ->
                                    ChannelListItem(
                                        channel = channel,
                                        playlistName = state.playlistTitles[channel.playlistUrl],
                                        onClick = { onChannelClick(channel) }
                                    )
                                }
                            }
                        }
                    }
                }
                if (state.liveStreams.isNotEmpty()) {
                    item(key = "live_section") {
                        ExpandableSection(
                            title = "Live Streams",
                            totalCount = state.liveStreams.size
                        ) { expanded ->
                            val visible = if (expanded) state.liveStreams else state.liveStreams.take(MAX_ITEMS)
                            Column {
                                visible.forEach { channel ->
                                    ChannelListItem(
                                        channel = channel,
                                        playlistName = state.playlistTitles[channel.playlistUrl],
                                        onClick = { onChannelClick(channel) }
                                    )
                                }
                            }
                        }
                    }
                }
                if (state.vod.isNotEmpty()) {
                    item(key = "vod_section") {
                        ExpandableSection(
                            title = "Video on Demand",
                            totalCount = state.vod.size
                        ) { expanded ->
                            val visible = if (expanded) state.vod else state.vod.take(MAX_ITEMS)
                            Column {
                                visible.forEach { channel ->
                                    ChannelListItem(
                                        channel = channel,
                                        playlistName = state.playlistTitles[channel.playlistUrl],
                                        onClick = { onChannelClick(channel) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandableSection(
    title: String,
    totalCount: Int,
    content: @Composable (expanded: Boolean) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = " ($totalCount)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp
                else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        content(expanded)
    }
}

@Composable
private fun ChannelListItem(
    channel: Channel,
    playlistName: String?,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = channel.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Column {
                if (channel.category.isNotBlank()) {
                    Text(
                        text = channel.category,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!playlistName.isNullOrBlank()) {
                    Text(
                        text = playlistName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
