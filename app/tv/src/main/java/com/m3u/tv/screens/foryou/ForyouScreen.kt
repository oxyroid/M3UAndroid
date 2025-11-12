package com.m3u.tv.screens.foryou

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CompactCard
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.m3u.business.foryou.ForyouViewModel
import com.m3u.business.foryou.Recommend
import com.m3u.core.foundation.components.AbsoluteSmoothCornerShape
import com.m3u.core.foundation.ui.SugarColors
import com.m3u.data.database.model.Playlist
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
        // Continue Watching Hero (Netflix-style) - User's #1 priority
        val continueWatching = specs.filterIsInstance<Recommend.CwSpec>().firstOrNull()
        if (continueWatching != null) {
            item(contentType = "ContinueWatchingHero") {
                ContinueWatchingHero(
                    channel = continueWatching.channel,
                    position = continueWatching.position,
                    duration = 0L, // Duration can be added from player state if available
                    onPlay = { navigateToChannel(continueWatching.channel.id) },
                    onInfo = { /* Navigate to channel details */ },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Unseen Favorites Row (Netflix-style horizontal carousel)
        val unseenChannels = specs.filterIsInstance<Recommend.UnseenSpec>().map { it.channel }
        if (unseenChannels.isNotEmpty()) {
            item(contentType = "UnseenFavoritesRow") {
                ContentRow(
                    title = "Unseen Favorites",
                    channels = unseenChannels.take(10), // Limit to 10 items for performance
                    onChannelClick = { channel -> navigateToChannel(channel.id) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Playlists Row (My Lists)
        item(contentType = "PlaylistsRow") {
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
