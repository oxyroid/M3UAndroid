package com.m3u.feature.favorite.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.m3u.data.database.model.Channel
import com.m3u.i18n.R.string
import com.m3u.material.ktx.tv
import com.m3u.material.model.LocalSpacing
import com.m3u.material.shape.AbsoluteSmoothCornerShape
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import androidx.tv.material3.ListItem as TvListItem
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

@Composable
internal fun FavoriteItem(
    channel: Channel,
    recently: Boolean,
    zapping: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tv = tv()
    if (!tv) {
        SmartphoneFavoriteItemImpl(
            channel = channel,
            recently = recently,
            zapping = zapping,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier
        )
    } else {
        TvFavouriteItemImpl(
            channel = channel,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier
        )
    }
}

@Composable
private fun SmartphoneFavoriteItemImpl(
    channel: Channel,
    recently: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    zapping: Boolean = false
) {
    val spacing = LocalSpacing.current

    val recentlyString = stringResource(string.ui_sort_recently)
    val neverPlayedString = stringResource(string.ui_sort_never_played)

    OutlinedCard(
        modifier = Modifier.semantics(mergeDescendants = true) { },
        border = CardDefaults.outlinedCardBorder(zapping),
        colors = CardDefaults.cardColors(Color.Transparent),
        shape = AbsoluteSmoothCornerShape(spacing.medium, 65),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = channel.title.trim(),
                    style = MaterialTheme.typography.titleSmall,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    fontWeight = FontWeight.Bold,
                )
            },
            supportingContent = {
                if (recently) {
                    Text(
                        text = remember(channel.seen) {
                            val now = Clock.System.now()
                            val instant = Instant.fromEpochMilliseconds(channel.seen)
                            val duration = now - instant
                            duration.toComponents { days, hours, minutes, seconds, _ ->
                                when {
                                    channel.seen == 0L -> neverPlayedString
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
            colors = ListItemDefaults.colors(Color.Transparent),
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
private fun TvFavouriteItemImpl(
    channel: Channel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvListItem(
        selected = false,
        onClick = onClick,
        onLongClick = onLongClick,
        headlineContent = {
            TvText(
                text = channel.title,
                style = TvMaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
        },
        modifier = modifier
    )
}
