package com.m3u.features.foryou.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m3u.material.components.Icon
import com.m3u.material.components.OuterRow
import com.m3u.material.components.TextBadge
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.UiMode
import com.m3u.ui.currentUiMode
import androidx.tv.material3.Card as TvCard
import androidx.tv.material3.MaterialTheme as TvMaterialTheme

@Composable
internal fun PlaylistItem(
    label: String,
    type: String?,
    typeWithSource: String?,
    number: Int,
    local: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (currentUiMode()) {
        UiMode.Default -> {
            PlaylistItemImpl(
                label = label,
                type = type,
                typeWithSource = typeWithSource,
                number = number,
                local = local,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = modifier
            )
        }

        UiMode.Television -> {
            TvPlaylistItemImpl(
                label = label,
                type = type,
                typeWithSource = typeWithSource,
                number = number,
                local = local,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = modifier
            )
        }

        UiMode.Compat -> {
            CompactPlaylistItemImpl(
                label = label,
                type = typeWithSource,
                number = number,
                local = local,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun PlaylistItemImpl(
    label: String,
    type: String?,
    typeWithSource: String?,
    number: Int,
    local: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val theme = MaterialTheme.colorScheme
    val currentContentColor by animateColorAsState(
        targetValue = theme.onSurface,
        label = "playlist-item-content"
    )
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
            verticalAlignment = Alignment.Top
        ) {
            if (local) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.DriveFileMove,
                    contentDescription = null,
                    tint = currentContentColor
                )
            }
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.alignByBaseline()
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        baselineShift = BaselineShift.None
                    ),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (typeWithSource != null) {
                    Text(
                        text = typeWithSource.uppercase(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            letterSpacing = 1.sp,
                            baselineShift = BaselineShift.Subscript,
                            fontFamily = FontFamily.Cursive,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp
                        ),
                        color = LocalContentColor.current.copy(0.67f)
                    )
                }
            }

            val currentPrimaryColor by animateColorAsState(
                targetValue = theme.primary,
                label = "playlist-item-primary"
            )
            val currentOnPrimaryColor by animateColorAsState(
                targetValue = theme.onPrimary,
                label = "playlist-item-on-primary"
            )
            Spacer(Modifier.weight(1f))
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
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
private fun TvPlaylistItemImpl(
    label: String,
    type: String?,
    typeWithSource: String?,
    number: Int,
    local: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val theme = TvMaterialTheme.colorScheme
    val currentContentColor by animateColorAsState(
        targetValue = theme.onSurface,
        label = "playlist-item-content"
    )
    TvCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
    ) {
        OuterRow(
            verticalAlignment = Alignment.Top
        ) {
            if (local) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.DriveFileMove,
                    contentDescription = null,
                    tint = currentContentColor
                )
            }
            Column {
                androidx.tv.material3.Text(
                    text = label,
                    style = TvMaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (typeWithSource != null) {
                    androidx.tv.material3.Text(
                        text = typeWithSource,
                        style = TvMaterialTheme.typography.bodySmall,
                        color = androidx.tv.material3.LocalContentColor.current.copy(0.45f)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

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

@Composable
private fun CompactPlaylistItemImpl(
    label: String,
    type: String?,
    number: Int,
    local: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        overlineContent = {
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
        headlineContent = {
            if (type != null) {
                TextBadge(type)
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