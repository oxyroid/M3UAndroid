package com.m3u.tv.screens.foryou

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ShapeDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.m3u.data.database.model.Channel
import com.m3u.tv.ui.theme.NetflixTvTheme

/**
 * Netflix-style horizontal content row for TV
 * Shows a row of content cards with title
 */
@Composable
fun ContentRow(
    title: String,
    channels: List<Channel>,
    onChannelClick: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = NetflixTvTheme.Spacing.small)
    ) {
        // Section title
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp
            ),
            color = Color.White,
            modifier = Modifier.padding(
                start = NetflixTvTheme.Spacing.medium,
                bottom = NetflixTvTheme.Spacing.small
            )
        )

        // Horizontal scrolling content
        LazyRow(
            modifier = Modifier.focusGroup(),
            contentPadding = PaddingValues(horizontal = NetflixTvTheme.Spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(NetflixTvTheme.Spacing.small)
        ) {
            items(channels.size) { index ->
                ContentCard(
                    channel = channels[index],
                    onClick = { onChannelClick(channels[index]) }
                )
            }
        }
    }
}

/**
 * Individual content card in the row
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ContentCard(
    channel: Channel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier
            .width(NetflixTvTheme.CardSize.width)
            .height(NetflixTvTheme.CardSize.height)
            .onFocusChanged { isFocused = it.hasFocus },
        shape = CardDefaults.shape(shape = ShapeDefaults.ExtraSmall),
        colors = CardDefaults.colors(
            containerColor = NetflixTvTheme.NetflixDarkGray,
            focusedContainerColor = NetflixTvTheme.NetflixDarkGray
        ),
        scale = CardDefaults.scale(
            focusedScale = 1.1f
        ),
        border = CardDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(
                    width = NetflixTvTheme.FocusBorderWidth,
                    color = Color.White
                )
            )
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Thumbnail image
            AsyncImage(
                model = channel.cover,
                contentDescription = channel.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        drawRect(NetflixTvTheme.CardScrim)
                    }
            )

            // Title at bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(NetflixTvTheme.Spacing.small),
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = channel.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (channel.category.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = channel.category,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 14.sp
                        ),
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Favorite indicator
            if (channel.favourite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(NetflixTvTheme.Spacing.small)
                        .background(
                            color = NetflixTvTheme.NetflixRed,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = "Favorite",
                        tint = Color.White,
                        modifier = Modifier.height(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Wide content card for featured content
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun WideContentCard(
    channel: Channel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier
            .width(NetflixTvTheme.CardSize.wideWidth)
            .height(NetflixTvTheme.CardSize.wideHeight)
            .onFocusChanged { isFocused = it.hasFocus },
        shape = CardDefaults.shape(shape = ShapeDefaults.ExtraSmall),
        colors = CardDefaults.colors(
            containerColor = NetflixTvTheme.NetflixDarkGray,
            focusedContainerColor = NetflixTvTheme.NetflixDarkGray
        ),
        scale = CardDefaults.scale(
            focusedScale = 1.1f
        ),
        border = CardDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(
                    width = NetflixTvTheme.FocusBorderWidth,
                    color = Color.White
                )
            )
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Background image
            AsyncImage(
                model = channel.cover,
                contentDescription = channel.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        drawRect(NetflixTvTheme.CardScrim)
                    }
            )

            // Content
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(NetflixTvTheme.Spacing.medium),
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = channel.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (channel.category.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = channel.category,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 16.sp
                        ),
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Favorite indicator
            if (channel.favourite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(NetflixTvTheme.Spacing.small)
                        .background(
                            color = NetflixTvTheme.NetflixRed,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = "Favorite",
                        tint = Color.White,
                        modifier = Modifier.height(24.dp)
                    )
                }
            }
        }
    }
}
