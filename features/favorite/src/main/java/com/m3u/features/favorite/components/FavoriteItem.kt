package com.m3u.features.favorite.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.data.database.model.Stream
import com.m3u.i18n.R.string
import com.m3u.material.components.Icon
import com.m3u.material.components.Image
import com.m3u.material.model.LocalSpacing
import com.m3u.material.shape.AbsoluteSmoothCornerShape
import com.m3u.ui.UiMode
import com.m3u.ui.currentUiMode
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun FavoriteItem(
    stream: Stream,
    recently: Boolean,
    zapping: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (currentUiMode()) {
        UiMode.Default -> {
            FavoriteItemImpl(
                stream = stream,
                recently = recently,
                zapping = zapping,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = modifier
            )
        }

        UiMode.Compat -> {
            CompactFavoriteItem(
                stream = stream,
                recently = recently,
                zapping = zapping,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = modifier
            )
        }

        else -> {}
    }
}

@Composable
private fun FavoriteItemImpl(
    stream: Stream,
    recently: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    zapping: Boolean = false
) {
    val spacing = LocalSpacing.current
    val pref = LocalPref.current

    val favourite = stream.favourite

    val recentlyString = stringResource(string.ui_sort_recently)
    val neverPlayedString = stringResource(string.ui_sort_never_played)

    val noPictureMode = pref.noPictureMode

    val star = remember(favourite) {
        movableContentOf {
            Crossfade(
                targetState = favourite,
                label = "stream-item-favourite"
            ) { favourite ->
                if (favourite) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        tint = Color(0xffffcd3c)
                    )
                }
            }
        }
    }

    OutlinedCard(
        modifier = Modifier.semantics(mergeDescendants = true) { },
        border = CardDefaults.outlinedCardBorder(zapping),
        shape = AbsoluteSmoothCornerShape(spacing.medium, 65),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = stream.title.trim(),
                    style = MaterialTheme.typography.titleSmall,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    fontWeight = FontWeight.Bold,
                )
            },
            leadingContent = if (!noPictureMode) {
                {
                    Image(
                        model = stream.cover,
                        transparentPlaceholder = true,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .height(56.dp)
                            .aspectRatio(1f)
                    )
                }
            } else null,
            supportingContent = {
                if (recently) {
                    Text(
                        text = remember(stream.seen) {
                            val now = Clock.System.now()
                            val instant = Instant.fromEpochMilliseconds(stream.seen)
                            val duration = now - instant
                            duration.toComponents { days, hours, minutes, seconds, _ ->
                                when {
                                    stream.seen == 0L -> neverPlayedString
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
            },
            trailingContent = {
                star()
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

@Composable
private fun CompactFavoriteItem(
    stream: Stream,
    recently: Boolean,
    zapping: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val favourite = stream.favourite
    val pref = LocalPref.current

    val recentlyString = stringResource(string.ui_sort_recently)
    val neverPlayedString = stringResource(string.ui_sort_never_played)

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
                AnimatedVisibility(!pref.noPictureMode && !stream.cover.isNullOrEmpty()) {
                    Image(
                        model = stream.cover,
                        transparentPlaceholder = true,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .height(56.dp)
                            .aspectRatio(1f)
                    )
                }
            },
            trailingContent = {
                Crossfade(
                    targetState = favourite,
                    label = "stream-item-favourite"
                ) { favourite ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (favourite) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = null,
                                tint = if (zapping) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.primary
                            )
                        }
                        AnimatedVisibility(recently) {
                            Text(
                                text = remember(stream.seen) {
                                    val now = Clock.System.now()
                                    val instant = Instant.fromEpochMilliseconds(stream.seen)
                                    val duration = now - instant
                                    duration.toComponents { days, hours, minutes, seconds, _ ->
                                        when {
                                            stream.seen == 0L -> neverPlayedString
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
