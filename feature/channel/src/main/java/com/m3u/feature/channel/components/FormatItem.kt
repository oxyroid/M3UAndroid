package com.m3u.feature.channel.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.common.C
import androidx.media3.common.Format

@Composable
internal fun FormatItem(
    format: Format,
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
                Text(format.displayText(type))
            },
            modifier = modifier.clickable { onClick() }
        )
    }
}

private fun Format.displayText(type: @C.TrackType Int): String = when (type) {
    C.TRACK_TYPE_AUDIO -> "$sampleRate $sampleMimeType"
    C.TRACK_TYPE_VIDEO -> "$width×$height $sampleMimeType"
    C.TRACK_TYPE_TEXT -> buildList {
        label?.let { add(it) }
        language?.let { add(it) }
        sampleMimeType?.let { add(it) }
    }
        .joinToString(separator = "-")

    else -> sampleMimeType.orEmpty()
}