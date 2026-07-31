package com.m3u.smartphone.ui.material.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.m3u.data.database.model.Channel
import com.m3u.i18n.R.plurals
import com.m3u.i18n.R.string
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

@Composable
internal fun Channel.playbackStatusText(): String {
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
