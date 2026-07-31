package com.m3u.smartphone.ui.business.foryou.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.preferenceOf
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.MediaKinds

@Composable
internal fun HomeChannelCard(
    channel: Channel,
    posterLayout: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val noPictureMode by preferenceOf(PreferencesKeys.NO_PICTURE_MODE)
    val width = if (posterLayout) 148.dp else 216.dp
    val artworkRatio = if (posterLayout) 2 / 3f else 16 / 9f
    val supporting = channel.subtitle
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: channel.productionYear?.toString()

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .width(width)
            .semantics(mergeDescendants = true) { }
            .clickable(
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = MaterialTheme.shapes.large,
            shadowElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(artworkRatio),
        ) {
            Box {
                val cover = channel.cover?.takeIf(String::isNotBlank)
                if (!noPictureMode && cover != null) {
                    SubcomposeAsyncImage(
                        model = remember(cover) {
                            ImageRequest.Builder(context)
                                .data(cover)
                                .crossfade(true)
                                .build()
                        },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        loading = {
                            HomeArtworkPlaceholder(channel = channel)
                        },
                        error = {
                            HomeArtworkPlaceholder(channel = channel)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    HomeArtworkPlaceholder(channel = channel)
                }
                if (channel.favourite) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                        contentColor = FavoriteColor,
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(6.dp)
                                .size(18.dp),
                        )
                    }
                }
            }
        }
        Text(
            text = channel.title.trim(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        supporting?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}

@Composable
private fun HomeArtworkPlaceholder(
    channel: Channel,
    modifier: Modifier = Modifier,
) {
    val icon = when (channel.mediaKind) {
        MediaKinds.LIVE -> Icons.Rounded.LiveTv
        MediaKinds.MOVIE -> Icons.Rounded.Movie
        MediaKinds.SERIES,
        MediaKinds.EPISODE -> Icons.Rounded.Tv
        else -> Icons.Rounded.PlayArrow
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(18.dp)
                    .size(32.dp),
            )
        }
    }
}

private val FavoriteColor = Color(0xFFFFCD3C)
