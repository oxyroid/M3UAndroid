package com.m3u.smartphone.ui.business.favourite.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.MediaKinds
import com.m3u.smartphone.ui.material.components.ChannelMediaCard
import com.m3u.smartphone.ui.material.components.ChannelMediaCardLayout
import com.m3u.smartphone.ui.material.components.playbackStatusText

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
        channel.playbackStatusText()
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
        supportingText = supportingText?.let(::AnnotatedString),
        zapping = zapping,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
    )
}
