package com.m3u.smartphone.ui.business.channel.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.media3.common.C
import androidx.media3.common.Format
import com.m3u.data.service.PlayerTrack
import com.m3u.i18n.R.string

@Composable
internal fun FormatItem(
    track: PlayerTrack,
    type: @C.TrackType Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    MaterialTheme(
        colorScheme = colorScheme.copy(
            surface = if (selected) colorScheme.onSurface else colorScheme.surface,
            onSurface = if (selected) colorScheme.surface else colorScheme.onSurface,
            surfaceVariant = if (selected) colorScheme.onSurfaceVariant else colorScheme.surfaceVariant,
            onSurfaceVariant = if (selected) colorScheme.surfaceVariant else colorScheme.onSurfaceVariant
        )
    ) {
        ListItem(
            headlineContent = {
                Text(track.format.displayText(type, stringResource(string.feat_channel_track_unknown)))
            },
            modifier = modifier.clickable { onClick() }
        )
    }
}

private fun Format.displayText(type: @C.TrackType Int, fallback: String): String = when (type) {
    C.TRACK_TYPE_AUDIO -> buildList {
        label?.takeIf { it.isNotBlank() }?.let { add(it) }
        language?.takeIf { it.isNotBlank() }?.let { add(it) }
        sampleRate.takeIf { it > 0 }?.let { add("$it Hz") }
        sampleMimeType?.takeIf { it.isNotBlank() }?.let { add(it) }
    }
        .joinToString(separator = " · ")

    C.TRACK_TYPE_VIDEO -> buildList {
        label?.takeIf { it.isNotBlank() }?.let { add(it) }
        if (width > 0 && height > 0) add("$width×$height")
        sampleMimeType?.takeIf { it.isNotBlank() }?.let { add(it) }
    }
        .joinToString(separator = " · ")

    C.TRACK_TYPE_TEXT -> buildList {
        label?.takeIf { it.isNotBlank() }?.let { add(it) }
        language?.takeIf { it.isNotBlank() }?.let { add(it) }
        sampleMimeType?.takeIf { it.isNotBlank() }?.let { add(it) }
    }
        .joinToString(separator = " · ")

    else -> sampleMimeType.orEmpty()
}.ifBlank { fallback }