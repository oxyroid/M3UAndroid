package com.m3u.features.playlist.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m3u.data.database.entity.Stream
import com.m3u.i18n.R.string
import com.m3u.material.components.Image
import com.m3u.material.components.TextBadge
import com.m3u.material.model.LocalSpacing
import java.net.URI

@Composable
internal fun StreamItem(
    stream: Stream,
    noPictureMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    zapping: Boolean = false,
) {
    val context = LocalContext.current
    val spacing = LocalSpacing.current
    val favourite = stream.favourite

    val scheme = remember(stream) {
        try {
            URI(stream.url).scheme
        } catch (ignored: Exception) {
            null
        } ?: context.getString(string.feat_playlist_scheme_unknown).uppercase()
    }

    OutlinedCard(
        modifier = Modifier.semantics(mergeDescendants = true) { },
        border = CardDefaults.outlinedCardBorder(zapping)
    ) {
        Column(
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .then(modifier)
        ) {
            AnimatedVisibility(
                visible = !noPictureMode && !stream.cover.isNullOrEmpty()
            ) {
                Image(
                    model = stream.cover,
                    errorPlaceholder = stream.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4 / 3f)
                )
            }
            Column(
                modifier = Modifier.padding(spacing.medium),
                verticalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stream.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontSize = MaterialTheme.typography.titleSmall.fontSize,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        fontWeight = FontWeight.Bold,
                        // icon-button-tokens: icon-size
                        modifier = Modifier
                            .heightIn(min = 24.dp)
                            .weight(1f)
                    )
                    Crossfade(
                        targetState = favourite,
                        label = "stream-item-favourite"
                    ) { favourite ->
                        if (favourite) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                ) {
                    TextBadge(scheme)
                    Text(
                        text = stream.url,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}


@Composable
internal fun CompactStreamItem(
    stream: Stream,
    noPictureMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    zapping: Boolean = false
) {
    val spacing = LocalSpacing.current
    val favourite = stream.favourite

    val colorScheme = MaterialTheme.colorScheme
    MaterialTheme(
        colorScheme = colorScheme.copy(
            surface = if (zapping) colorScheme.onSurface else colorScheme.surface,
            onSurface = if (zapping) colorScheme.surface else colorScheme.onSurface,
            surfaceVariant = if (zapping) colorScheme.onSurfaceVariant else colorScheme.surfaceVariant,
            onSurfaceVariant = if (zapping) colorScheme.surfaceVariant else colorScheme.onSurfaceVariant
        )
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = stream.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontSize = MaterialTheme.typography.titleSmall.fontSize,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    fontWeight = FontWeight.Bold
                )
            },
            supportingContent = {
                Text(
                    text = stream.url,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingContent = {
                AnimatedVisibility(!noPictureMode && !stream.cover.isNullOrEmpty()) {
                    Image(
                        model = stream.cover,
                        errorPlaceholder = stream.title,
                        contentScale = ContentScale.Crop,
                        shape = RoundedCornerShape(spacing.small),
                        modifier = Modifier.size(48.dp)
                    )
                }
            },
            trailingContent = {
                Crossfade(
                    targetState = favourite,
                    label = "stream-item-favourite"
                ) { favourite ->
                    if (favourite) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            tint = if (zapping) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .then(modifier)
        )
    }
}
