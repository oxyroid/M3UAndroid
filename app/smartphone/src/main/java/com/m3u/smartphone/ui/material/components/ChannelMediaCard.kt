package com.m3u.smartphone.ui.material.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.preferenceOf
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.MediaKinds

internal enum class ChannelMediaCardLayout(
    val artworkAspectRatio: Float,
) {
    LANDSCAPE(16 / 9f),
    POSTER(2 / 3f),
}

@Composable
internal fun ChannelMediaCard(
    channel: Channel,
    layout: ChannelMediaCardLayout,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    artwork: Any? = channel.cover,
    supportingText: AnnotatedString? = channel.subtitle
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let(::AnnotatedString)
        ?: channel.productionYear?.toString()?.let(::AnnotatedString),
    forceCompact: Boolean = false,
    zapping: Boolean = false,
) {
    val context = LocalContext.current
    val noPictureMode by preferenceOf(PreferencesKeys.NO_PICTURE_MODE)
    val outlineColor by animateColorAsState(
        targetValue = if (zapping) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        },
        label = "channel-media-card-outline",
    )
    val interactionModifier = if (onLongClick == null) {
        Modifier.clickable(
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        Modifier.combinedClickable(
            role = Role.Button,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    }

    val cardModifier = modifier
        .semantics(mergeDescendants = true) { }
        .then(interactionModifier)
    val resolvedArtwork = artwork?.takeUnless {
        it is String && it.isBlank()
    }
    if (noPictureMode || forceCompact || resolvedArtwork == null) {
        CompactChannelMediaCard(
            channel = channel,
            artwork = resolvedArtwork.takeUnless { noPictureMode },
            supportingText = supportingText,
            zapping = zapping,
            outlineColor = outlineColor,
            modifier = cardModifier,
        )
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = cardModifier,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = MaterialTheme.shapes.large,
                shadowElevation = if (zapping) 3.dp else 1.dp,
                border = BorderStroke(
                    width = if (zapping) 2.dp else 1.dp,
                    color = outlineColor,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(layout.artworkAspectRatio),
            ) {
                Box {
                    SubcomposeAsyncImage(
                        model = remember(resolvedArtwork) {
                            ImageRequest.Builder(context)
                                .data(resolvedArtwork)
                                .crossfade(true)
                                .build()
                        },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        loading = {
                            ChannelArtworkPlaceholder(channel = channel)
                        },
                        error = {
                            ChannelArtworkPlaceholder(channel = channel)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
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
                    if (zapping) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = MaterialTheme.shapes.extraLarge,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
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
            supportingText
                ?.takeIf { it.text.isNotBlank() }
                ?.let { supporting ->
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }
        }
    }
}

@Composable
private fun CompactChannelMediaCard(
    channel: Channel,
    artwork: Any?,
    supportingText: AnnotatedString?,
    zapping: Boolean,
    outlineColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        shadowElevation = if (zapping) 3.dp else 1.dp,
        border = BorderStroke(
            width = if (zapping) 2.dp else 1.dp,
            color = outlineColor,
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 104.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.size(56.dp),
            ) {
                if (artwork == null) {
                    ChannelTypeIcon(channel = channel)
                } else {
                    SubcomposeAsyncImage(
                        model = artwork,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        loading = {
                            ChannelTypeIcon(channel = channel)
                        },
                        error = {
                            ChannelTypeIcon(channel = channel)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = channel.title.trim(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                supportingText
                    ?.takeIf { it.text.isNotBlank() }
                    ?.let { supporting ->
                        Text(
                            text = supporting,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
            }
            if (channel.favourite) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    tint = FavoriteColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ChannelArtworkPlaceholder(
    channel: Channel,
    modifier: Modifier = Modifier,
) {
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
            ChannelTypeIcon(
                channel = channel,
                modifier = Modifier.size(68.dp),
            )
        }
    }
}

@Composable
private fun ChannelTypeIcon(
    channel: Channel,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        Icon(
            imageVector = channel.typeIcon(),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )
    }
}

private fun Channel.typeIcon() = when (mediaKind) {
    MediaKinds.LIVE -> Icons.Rounded.LiveTv
    MediaKinds.MOVIE -> Icons.Rounded.Movie
    MediaKinds.SERIES,
    MediaKinds.EPISODE -> Icons.Rounded.Tv
    else -> Icons.Rounded.PlayArrow
}

private val FavoriteColor = Color(0xFFFFCD3C)
