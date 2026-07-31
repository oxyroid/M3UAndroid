package com.m3u.smartphone.ui.business.favourite.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.MediaKinds
import com.m3u.i18n.R.plurals
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.material.components.ChannelMediaCard
import com.m3u.smartphone.ui.material.components.ChannelMediaCardLayout
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

@Composable
internal fun FavoriteItem(
    channel: Channel,
    recently: Boolean,
    zapping: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = when (channel.mediaKind) {
        MediaKinds.MOVIE,
        MediaKinds.SERIES -> ChannelMediaCardLayout.POSTER
        else -> ChannelMediaCardLayout.LANDSCAPE
    }
    val playbackStatus = if (recently) {
        channel.playbackStatus()
    } else {
        null
    }
    val supportingText = listOfNotNull(
        channel.category.trim().takeIf(String::isNotEmpty),
        playbackStatus,
    ).joinToString(" · ").ifBlank {
        channel.subtitle
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: channel.productionYear?.toString().orEmpty()
    }.takeIf(String::isNotEmpty)

    ChannelMediaCard(
        channel = channel,
        layout = layout,
        supportingText = supportingText,
        zapping = zapping,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
    )
}

@Composable
private fun Channel.playbackStatus(): String {
    if (seen == 0L) {
        return stringResource(string.ui_sort_never_played)
    }
    val elapsed = remember(seen) {
        Clock.System.now() - Instant.fromEpochMilliseconds(seen)
    }.coerceAtLeast(Duration.ZERO)
    val days = elapsed.inWholeDays
    val hours = elapsed.inWholeHours
    val minutes = elapsed.inWholeMinutes
    return when {
        days > 0 -> pluralStringResource(
            plurals.feat_favorite_played_days_ago,
            days.toInt(),
            days,
        )
        hours > 0 -> pluralStringResource(
            plurals.feat_favorite_played_hours_ago,
            hours.toInt(),
            hours,
        )
        minutes > 0 -> pluralStringResource(
            plurals.feat_favorite_played_minutes_ago,
            minutes.toInt(),
            minutes,
        )
        else -> stringResource(string.feat_favorite_played_just_now)
    }
}
