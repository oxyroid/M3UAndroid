package com.m3u.features.favorite.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.data.database.model.Stream
import com.m3u.i18n.R.string
import com.m3u.material.components.Image
import com.m3u.material.components.TextBadge
import com.m3u.material.ktx.isTelevision
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.Sort
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.net.URI
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun FavoriteItem(
    stream: Stream,
    noPictureMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    zapping: Boolean = false,
    sort: Sort
) {
    val pref = LocalPref.current
    val compact = pref.compact

    if (!compact) {
        FavoriteItemImpl(
            stream = stream,
            noPictureMode = noPictureMode,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier,
            zapping = zapping,
            sort = sort
        )
    } else {
        CompactFavoriteItemImpl(
            stream = stream,
            noPictureMode = noPictureMode,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier,
            zapping = zapping,
            sort = sort
        )
    }
}

@Composable
private fun FavoriteItemImpl(
    stream: Stream,
    noPictureMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    zapping: Boolean = false,
    sort: Sort
) {
    val context = LocalContext.current
    val spacing = LocalSpacing.current
    val tv = isTelevision()
    val recently = sort == Sort.RECENTLY
    val recentlyString = stringResource(string.ui_sort_recently)

    val scheme = remember(stream.url) {
        URI(stream.url).scheme ?: context.getString(string.feat_playlist_scheme_unknown)
    }

    if (!tv) {
        OutlinedCard(
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
                AnimatedVisibility(!noPictureMode && !stream.cover.isNullOrEmpty()) {
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
                    Column {
                        Text(
                            text = stream.title.trim(),
                            style = MaterialTheme.typography.titleSmall,
                            fontSize = MaterialTheme.typography.titleSmall.fontSize,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                            fontWeight = FontWeight.Bold,
                        )
                        if (recently) {
                            Text(
                                text = remember(stream.seen) {
                                    val now = Clock.System.now()
                                    val instant = Instant.fromEpochMilliseconds(stream.seen)
                                    val duration = now - instant
                                    duration.toComponents { days, hours, minutes, seconds, _ ->
                                        when {
                                            days > 0 -> days.days.toString()
                                            hours > 0 -> hours.hours.toString()
                                            minutes > 0 -> minutes.minutes.toString()
                                            seconds > 0 -> seconds.seconds.toString()
                                            else -> recentlyString
                                        }
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalContentColor.current.copy(0.56f)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                    ) {
                        TextBadge(scheme)
                        Text(
                            text = stream.url.trim(),
                            maxLines = 1,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    } else {
        Card(
            onClick = onClick,
            onLongClick = onLongClick
        ) {
            AnimatedVisibility(!noPictureMode && !stream.cover.isNullOrEmpty()) {
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
                Column(
                    // icon-button-tokens: icon-size
                    modifier = Modifier
                        .heightIn(min = 24.dp)
                        .weight(1f)
                ) {
                    Text(
                        text = stream.title.trim(),
                        style = MaterialTheme.typography.titleSmall,
                        fontSize = MaterialTheme.typography.titleSmall.fontSize,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        fontWeight = FontWeight.Bold,
                    )
                    if (recently) {
                        Text(
                            text = remember(stream.seen) {
                                val now = Clock.System.now()
                                val instant = Instant.fromEpochMilliseconds(stream.seen)
                                val duration = now - instant
                                duration.toComponents { days, hours, minutes, seconds, _ ->
                                    when {
                                        days > 0 -> days.days.toString()
                                        hours > 0 -> hours.hours.toString()
                                        minutes > 0 -> minutes.minutes.toString()
                                        seconds > 0 -> seconds.seconds.toString()
                                        else -> recentlyString
                                    }
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalContentColor.current.copy(0.56f)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                ) {
                    TextBadge(scheme)
                    Text(
                        text = stream.url.trim(),
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
private fun CompactFavoriteItemImpl(
    stream: Stream,
    noPictureMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    zapping: Boolean = false,
    sort: Sort
) {
    val spacing = LocalSpacing.current
    val colorScheme = MaterialTheme.colorScheme

    val recently = sort == Sort.RECENTLY
    val recentlyString = stringResource(string.ui_sort_recently)

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
                    text = stream.title.trim(),
                    style = MaterialTheme.typography.titleSmall,
                    fontSize = MaterialTheme.typography.titleSmall.fontSize,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    fontWeight = FontWeight.Bold
                )
            },
            supportingContent = {
                Text(
                    text = stream.url.trim(),
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
                if (recently) {
                    Text(
                        text = remember(stream.seen) {
                            val now = Clock.System.now()
                            val instant = Instant.fromEpochMilliseconds(stream.seen)
                            val duration = now - instant
                            duration.toComponents { days, hours, minutes, seconds, _ ->
                                when {
                                    days > 0 -> "$days d"
                                    hours > 0 -> "$hours h"
                                    minutes > 0 -> "$minutes m"
                                    seconds > 0 -> "$seconds s"
                                    else -> recentlyString
                                }
                            }
                        },
                        color = if (zapping) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
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