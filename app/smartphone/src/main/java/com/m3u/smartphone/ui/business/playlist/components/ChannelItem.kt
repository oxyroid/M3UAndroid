package com.m3u.smartphone.ui.business.playlist.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.preferenceOf
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.MediaKinds
import com.m3u.data.database.model.Programme
import com.m3u.smartphone.TimeUtils.formatEOrSh
import com.m3u.smartphone.ui.material.components.ChannelMediaCard
import com.m3u.smartphone.ui.material.components.ChannelMediaCardLayout
import com.m3u.smartphone.ui.material.components.playbackStatusText
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@Composable
internal fun ChannelItem(
    channel: Channel,
    recently: Boolean,
    zapping: Boolean,
    cover: Any?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    programme: Programme?,
    modifier: Modifier = Modifier,
    isVodOrSeriesPlaylist: Boolean = true,
) {
    val usesArtworkCard = channel.usesArtworkCard(isVodOrSeriesPlaylist)
    val supportingText = when {
        recently -> AnnotatedString(channel.playbackStatusText())
        programme != null -> programme.readText()
        else -> channel.fallbackSupportingText()
    }

    ChannelMediaCard(
        channel = channel,
        artwork = cover,
        layout = if (usesArtworkCard) {
            ChannelMediaCardLayout.POSTER
        } else {
            ChannelMediaCardLayout.LANDSCAPE
        },
        supportingText = supportingText,
        forceCompact = !usesArtworkCard,
        zapping = zapping,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
    )
}

internal fun Channel.usesArtworkCard(
    isVodOrSeriesPlaylist: Boolean,
): Boolean {
    val isPosterMedia = mediaKind == MediaKinds.MOVIE ||
        mediaKind == MediaKinds.SERIES ||
        mediaKind == MediaKinds.UNKNOWN && isVodOrSeriesPlaylist
    return isPosterMedia && !cover.isNullOrBlank()
}

private fun Channel.fallbackSupportingText(): AnnotatedString? {
    val normalizedTitle = title.trim()
    val metadata = listOfNotNull(
        subtitle
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it != normalizedTitle },
        productionYear?.toString(),
    ).distinct()
        .joinToString(" · ")
        .ifBlank { category.trim() }
        .takeIf(String::isNotEmpty)
    return metadata?.let(::AnnotatedString)
}

@Composable
internal fun Programme.readText(
    timeColor: Color = MaterialTheme.colorScheme.secondary,
): AnnotatedString = buildAnnotatedString {
    val clockMode by preferenceOf(PreferencesKeys.CLOCK_MODE)
    val formatLocale = LocalConfiguration.current.locales[0]

    val start = Instant.fromEpochMilliseconds(start)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .formatEOrSh(clockMode, locale = formatLocale)
    withStyle(
        SpanStyle(color = timeColor, fontWeight = FontWeight.SemiBold),
    ) {
        append("[$start] ")
    }
    append(title)
}
