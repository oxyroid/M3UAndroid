package com.m3u.smartphone.ui.business.foryou.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.m3u.data.database.model.Channel
import com.m3u.smartphone.ui.material.model.LocalSpacing
import com.m3u.smartphone.ui.material.model.NetflixTheme
import com.m3u.smartphone.ui.material.model.scrim

/**
 * Netflix-style horizontal carousel for content
 */
@Composable
fun ContentCarousel(
    title: String,
    channels: List<Channel>,
    onChannelClick: (Channel) -> Unit,
    onChannelLongClick: (Channel) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Section title
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = Color.White,
            modifier = Modifier.padding(horizontal = spacing.medium)
        )

        Spacer(modifier = Modifier.height(spacing.small))

        // Horizontal scrolling content
        LazyRow(
            contentPadding = PaddingValues(horizontal = spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            items(channels) { channel ->
                ContentCard(
                    channel = channel,
                    onClick = { onChannelClick(channel) },
                    onLongClick = { onChannelLongClick(channel) }
                )
            }
        }
    }
}

/**
 * Individual content card in the carousel
 */
@Composable
private fun ContentCard(
    channel: Channel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.05f else 1f,
        label = "cardScale"
    )

    Box(
        modifier = modifier
            .width(150.dp)
            .height(220.dp)
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                onClick = onClick,
                onClickLabel = channel.title
            )
    ) {
        // Thumbnail image
        AsyncImage(
            model = channel.cover,
            contentDescription = channel.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
        )

        // Gradient overlay for text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scrim(NetflixTheme.BottomScrim)
        )

        // Title at bottom
        Text(
            text = channel.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(spacing.small)
        )

        // Favorite indicator
        if (channel.favourite) {
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = "Favorite",
                tint = NetflixTheme.NetflixRed,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(spacing.small)
            )
        }
    }
}

/**
 * Wide content card for featured content (similar to Netflix VOD)
 */
@Composable
fun WideContentCard(
    channel: Channel,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.03f else 1f,
        label = "wideCardScale"
    )

    Box(
        modifier = modifier
            .width(280.dp)
            .height(158.dp)
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                onClick = onClick,
                onClickLabel = channel.title
            )
    ) {
        // Background image
        AsyncImage(
            model = channel.cover,
            contentDescription = channel.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scrim(NetflixTheme.BottomScrim)
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.medium),
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = channel.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (channel.category.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = channel.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Favorite indicator
        if (channel.favourite) {
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = "Favorite",
                tint = NetflixTheme.NetflixRed,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(spacing.small)
            )
        }
    }
}

/**
 * Carousel with wide cards (for VOD/Movies)
 */
@Composable
fun WideContentCarousel(
    title: String,
    channels: List<Channel>,
    onChannelClick: (Channel) -> Unit,
    onChannelLongClick: (Channel) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Section title
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = Color.White,
            modifier = Modifier.padding(horizontal = spacing.medium)
        )

        Spacer(modifier = Modifier.height(spacing.small))

        // Horizontal scrolling content
        LazyRow(
            contentPadding = PaddingValues(horizontal = spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            items(channels) { channel ->
                WideContentCard(
                    channel = channel,
                    onClick = { onChannelClick(channel) },
                    onLongClick = { onChannelLongClick(channel) }
                )
            }
        }
    }
}

/**
 * Category preview carousel (for browsing playlists/categories)
 */
@Composable
fun CategoryCarousel(
    title: String,
    categories: List<Pair<String, Int>>, // Category name to count
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Section title
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = Color.White,
            modifier = Modifier.padding(horizontal = spacing.medium)
        )

        Spacer(modifier = Modifier.height(spacing.small))

        // Horizontal scrolling categories
        LazyRow(
            contentPadding = PaddingValues(horizontal = spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            items(categories) { (category, count) ->
                CategoryCard(
                    name = category,
                    count = count,
                    onClick = { onCategoryClick(category) }
                )
            }
        }
    }
}

/**
 * Category card
 */
@Composable
private fun CategoryCard(
    name: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current

    Box(
        modifier = modifier
            .width(140.dp)
            .height(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        // Gradient background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scrim(
                    Brush.horizontalGradient(
                        colors = listOf(
                            NetflixTheme.NetflixGray,
                            NetflixTheme.NetflixDarkGray
                        )
                    )
                )
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.medium),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$count items",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}
