package com.m3u.smartphone.ui.business.foryou.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.m3u.business.foryou.Recommend
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist
import com.m3u.smartphone.ui.material.model.LocalSpacing
import com.m3u.smartphone.ui.material.model.NetflixTheme

/**
 * Netflix-style home screen layout
 * Features:
 * - Large Continue Watching hero at top
 * - Horizontal carousels for different categories
 * - Smooth scrolling with proper spacing
 * - Dark theme with gradients
 */
@Composable
fun NetflixStyleHome(
    specs: List<Recommend.Spec>,
    playlists: Map<Playlist, Int>,
    onPlayChannel: (Channel) -> Unit,
    onNavigateToPlaylist: (Playlist) -> Unit,
    onChannelLongClick: (Channel) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current

    // Separate specs by type
    val continueWatching = remember(specs) {
        specs.filterIsInstance<Recommend.CwSpec>().firstOrNull()
    }
    val unseenChannels = remember(specs) {
        specs.filterIsInstance<Recommend.UnseenSpec>().map { it.channel }
    }

    // Group channels by category from playlists
    val channelsByPlaylist = remember(playlists) {
        playlists.filter { it.value > 0 }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NetflixTheme.NetflixBlack,
                        NetflixTheme.NetflixDarkGray,
                        NetflixTheme.NetflixBlack
                    )
                )
            )
    ) {
        LazyColumn(
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(spacing.large),
            modifier = Modifier.fillMaxSize()
        ) {
            // Continue Watching Hero (if available)
            continueWatching?.let { cw ->
                item(key = "continue-watching") {
                    Column {
                        Spacer(modifier = Modifier.height(spacing.small))
                        ContinueWatchingHero(
                            channel = cw.channel,
                            position = cw.position,
                            onClick = { onPlayChannel(cw.channel) },
                            modifier = Modifier.padding(horizontal = spacing.medium)
                        )
                    }
                }
            }

            // Unseen Favorites
            if (unseenChannels.isNotEmpty()) {
                item(key = "unseen-favorites") {
                    ContentCarousel(
                        title = "Unseen Favorites",
                        channels = unseenChannels,
                        onChannelClick = onPlayChannel,
                        onChannelLongClick = onChannelLongClick
                    )
                }
            }

            // Playlists as carousels
            // We'll show them as category cards since we don't have channel data here
            if (channelsByPlaylist.isNotEmpty()) {
                item(key = "my-lists") {
                    PlaylistCarousel(
                        title = "My Lists",
                        playlists = channelsByPlaylist,
                        onPlaylistClick = onNavigateToPlaylist
                    )
                }
            }

            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(spacing.large))
            }
        }
    }
}

/**
 * Playlist carousel showing available playlists
 */
@Composable
private fun PlaylistCarousel(
    title: String,
    playlists: Map<Playlist, Int>,
    onPlaylistClick: (Playlist) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = Color.White,
            modifier = Modifier.padding(horizontal = spacing.medium)
        )

        Spacer(modifier = Modifier.height(spacing.small))

        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            items(playlists.toList()) { (playlist, count) ->
                PlaylistCard(
                    playlist = playlist,
                    count = count,
                    onClick = { onPlaylistClick(playlist) }
                )
            }
        }
    }
}

/**
 * Individual playlist card
 */
@Composable
private fun PlaylistCard(
    playlist: Playlist,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current

    Box(
        modifier = modifier
            .width(160.dp)
            .height(90.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        NetflixTheme.NetflixGray,
                        NetflixTheme.NetflixDarkGray
                    )
                ),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(spacing.medium)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$count channels",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}
