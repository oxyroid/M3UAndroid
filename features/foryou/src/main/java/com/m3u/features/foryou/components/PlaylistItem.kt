package com.m3u.features.foryou.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.material.components.OuterRow
import com.m3u.material.ktx.isTelevision
import com.m3u.material.model.LocalSpacing
import androidx.tv.material3.Card as TvCard

@Composable
internal fun PlaylistItem(
    label: String,
    number: Int,
    local: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pref = LocalPref.current
    val compact = pref.compact

    if (!compact) {
        PlaylistItemImpl(
            label = label,
            number = number,
            local = local,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier
        )
    } else {
        CompactPlaylistItemImpl(
            label = label,
            number = number,
            local = local,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier
        )
    }
}

@Composable
private fun PlaylistItemImpl(
    label: String,
    number: Int,
    local: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val theme = MaterialTheme.colorScheme
    val tv = isTelevision()
    val currentContentColor by animateColorAsState(
        targetValue = theme.onSurface,
        label = "playlist-item-content"
    )

    if (!tv) {
        OutlinedCard(
            shape = RoundedCornerShape(spacing.medium),
            border = CardDefaults.outlinedCardBorder(local),
            modifier = modifier.semantics(mergeDescendants = true) { }
        ) {
            OuterRow(
                modifier = Modifier
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (local) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.DriveFileMove,
                        contentDescription = null,
                        tint = currentContentColor
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                val currentPrimaryColor by animateColorAsState(
                    targetValue = theme.primary,
                    label = "playlist-item-primary"
                )
                val currentOnPrimaryColor by animateColorAsState(
                    targetValue = theme.onPrimary,
                    label = "playlist-item-on-primary"
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(currentPrimaryColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        color = currentOnPrimaryColor,
                        text = number.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(
                            start = spacing.small,
                            end = spacing.small,
                            bottom = 2.dp,
                        ),
                        softWrap = false,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    } else {
        TvCard(
            onClick = onClick,
            onLongClick = onLongClick
        ) {
            OuterRow(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (local) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.DriveFileMove,
                        contentDescription = null,
                        tint = currentContentColor
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                val currentPrimaryColor by animateColorAsState(
                    targetValue = theme.primary,
                    label = "playlist-item-primary"
                )
                val currentOnPrimaryColor by animateColorAsState(
                    targetValue = theme.onPrimary,
                    label = "playlist-item-on-primary"
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(currentPrimaryColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        color = currentOnPrimaryColor,
                        text = number.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(
                            start = spacing.small,
                            end = spacing.small,
                            bottom = 2.dp,
                        ),
                        softWrap = false,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactPlaylistItemImpl(
    label: String,
    number: Int,
    local: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            if (local) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.DriveFileMove,
                    contentDescription = null
                )
            }
        },
        trailingContent = {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                textAlign = TextAlign.Center
            )
        },
        modifier = Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .then(modifier)
    )
}