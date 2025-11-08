package com.m3u.tv.screens.foryou

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CompactCard
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.m3u.business.foryou.ForyouViewModel
import com.m3u.business.foryou.Recommend
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.preferenceOf
import com.m3u.core.foundation.components.AbsoluteSmoothCornerShape
import com.m3u.core.foundation.ui.SugarColors
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.type
import com.m3u.tv.screens.dashboard.rememberChildPadding
import com.m3u.tv.theme.LexendExa

@Composable
fun ForyouScreen(
    navigateToPlaylist: (playlistUrl: String) -> Unit,
    navigateToChannel: (channelId: Int) -> Unit,
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    isTopBarVisible: Boolean,
    viewModel: ForyouViewModel = hiltViewModel(),
) {
    val playlists: Map<Playlist, Int> by viewModel.playlists.collectAsStateWithLifecycle()
    val specs: List<Recommend.Spec> by viewModel.specs.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        Catalog(
            playlists = playlists,
            specs = specs,
            onScroll = onScroll,
            navigateToPlaylist = navigateToPlaylist,
            navigateToChannel = navigateToChannel,
            isTopBarVisible = isTopBarVisible,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun Catalog(
    playlists: Map<Playlist, Int>,
    specs: List<Recommend.Spec>,
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    navigateToPlaylist: (playlistUrl: String) -> Unit,
    navigateToChannel: (channelId: Int) -> Unit,
    modifier: Modifier = Modifier,
    isTopBarVisible: Boolean = true,
) {
    val contentTypeMode by preferenceOf(PreferencesKeys.CONTENT_TYPE_MODE)
    val lazyListState = rememberLazyListState()
    val childPadding = rememberChildPadding()

    val shouldShowTopBar by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 &&
                    lazyListState.firstVisibleItemScrollOffset < 300
        }
    }

    LaunchedEffect(shouldShowTopBar) {
        onScroll(shouldShowTopBar)
    }
    LaunchedEffect(isTopBarVisible) {
        if (isTopBarVisible) lazyListState.animateScrollToItem(0)
    }

    LazyColumn(
        state = lazyListState,
        contentPadding = PaddingValues(bottom = 108.dp),
        modifier = modifier
    ) {
        if (specs.isNotEmpty()) {
            item(contentType = "FeaturedChannelsCarousel") {
                FeaturedSpecsCarousel(
                    specs = specs,
                    padding = childPadding,
                    onClickSpec = { spec ->
                        when (spec) {
                            is Recommend.UnseenSpec -> {
                                navigateToChannel(spec.channel.id)
                            }

                            is Recommend.DiscoverSpec -> TODO()
                            is Recommend.NewRelease -> TODO()
                            is Recommend.CwSpec -> TODO()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(324.dp)
                    /*
                     Setting height for the FeaturedChannelCarousel to keep it rendered with same height,
                     regardless of the top bar's visibility
                     */
                )
            }
        }

        item(contentType = "PlaylistsRow") {
            if (contentTypeMode) {
                // Show Content Type Cards (Live TV, Movies, Series)
                // Filter to only show Xtream playlists with specific types
                val xtreamPlaylists = playlists.keys.filter {
                    it.source == DataSource.Xtream && it.type != null
                }

                ContentTypeCardsRow(
                    playlists = xtreamPlaylists.associateWith { playlists[it] ?: 0 },
                    navigateToPlaylist = navigateToPlaylist,
                    modifier = Modifier.padding(top = 16.dp)
                )
            } else {
                // Show Traditional Playlist Cards
                val startPadding: Dp = rememberChildPadding().start
                val endPadding: Dp = rememberChildPadding().end
                val shape = AbsoluteSmoothCornerShape(16.dp, 100)
                LazyRow(
                    modifier = Modifier
                        .focusGroup()
                        .padding(top = 16.dp),
                    contentPadding = PaddingValues(start = startPadding, end = endPadding)
                ) {
                    val entries = playlists.entries.toList()
                    items(entries.size) {
                        val (playlist, _) = entries[it]
                        val (color, contentColor) = remember {
                            SugarColors.entries.random()
                        }
                        CompactCard(
                            onClick = { navigateToPlaylist(playlist.url) },
                            title = {
                                Text(
                                    text = playlist.title,
                                    modifier = Modifier.padding(16.dp),
                                    fontSize = 36.sp,
                                    lineHeight = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = LexendExa
                                )
                            },
                            colors = CardDefaults.compactCardColors(
                                containerColor = color,
                                contentColor = MaterialTheme.colorScheme.background
                            ),
                            shape = CardDefaults.shape(shape),
                            border = CardDefaults.border(
                                border = Border(
                                    BorderStroke(
                                        width = 2.dp,
                                        color = MaterialTheme.colorScheme.border
                                    ),
                                    shape = shape
                                ),
                                focusedBorder = Border(
                                    BorderStroke(width = 4.dp, color = Color.White),
                                    shape = shape
                                ),
                                pressedBorder = Border(
                                    BorderStroke(
                                        width = 4.dp,
                                        color = MaterialTheme.colorScheme.border
                                    ),
                                    shape = shape
                                )
                            ),
                            image = {},
                            modifier = Modifier
                                .width(265.dp)
                                .heightIn(min = 130.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ContentTypeCardsRow(
    playlists: Map<Playlist, Int>,
    navigateToPlaylist: (playlistUrl: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val startPadding: Dp = rememberChildPadding().start
    val endPadding: Dp = rememberChildPadding().end

    // Find type-specific Xtream playlists
    val livePlaylist = playlists.keys.firstOrNull {
        it.source == DataSource.Xtream && it.type == DataSource.Xtream.TYPE_LIVE
    }
    val vodPlaylist = playlists.keys.firstOrNull {
        it.source == DataSource.Xtream && it.type == DataSource.Xtream.TYPE_VOD
    }
    val seriesPlaylist = playlists.keys.firstOrNull {
        it.source == DataSource.Xtream && it.type == DataSource.Xtream.TYPE_SERIES
    }

    if (livePlaylist == null && vodPlaylist == null && seriesPlaylist == null) {
        // Show warning if no type-specific Xtream playlists found
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = startPadding, vertical = 16.dp)
                .background(
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    MaterialTheme.shapes.medium
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "⚠️ Content Type Mode requires Xtream Codes playlist.\nPlease add an Xtream playlist or disable Content Type Mode in Settings.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium
            )
        }
        return
    }

    LazyRow(
        modifier = modifier.focusGroup(),
        contentPadding = PaddingValues(start = startPadding, end = endPadding),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Live TV Card
        if (livePlaylist != null) {
            item {
                ContentTypeCard(
                    title = "Live TV",
                    subtitle = "${playlists[livePlaylist] ?: 0} channels",
                    icon = Icons.Rounded.LiveTv,
                    containerColor = Color(0xFF10B981), // Teal/Green
                    onClick = { navigateToPlaylist(livePlaylist.url) }
                )
            }
        }

        // Movies Card
        if (vodPlaylist != null) {
            item {
                ContentTypeCard(
                    title = "Movies",
                    subtitle = "${playlists[vodPlaylist] ?: 0} movies",
                    icon = Icons.Rounded.Movie,
                    containerColor = Color(0xFFA855F7), // Purple
                    onClick = { navigateToPlaylist(vodPlaylist.url) }
                )
            }
        }

        // Series Card
        if (seriesPlaylist != null) {
            item {
                ContentTypeCard(
                    title = "Series",
                    subtitle = "${playlists[seriesPlaylist] ?: 0} series",
                    icon = Icons.Rounded.Tv,
                    containerColor = Color(0xFFF97316), // Orange
                    onClick = { navigateToPlaylist(seriesPlaylist.url) }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ContentTypeCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .width(320.dp)
            .height(180.dp),
        colors = CardDefaults.colors(
            containerColor = containerColor,
            contentColor = Color.White
        ),
        shape = CardDefaults.shape(MaterialTheme.shapes.medium),
        border = CardDefaults.border(
            focusedBorder = Border(
                BorderStroke(4.dp, Color.White),
                shape = MaterialTheme.shapes.medium
            )
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(64.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}
