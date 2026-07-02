package com.m3u.smartphone.ui.business.channel.components

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
    C.TRACK_TYPE_AUDIO -> buildList {
        label?.takeIf { it.isNotBlank() }?.let { add(it) }
        language?.takeIf { it.isNotBlank() }?.let { add(it) }
        if (channelCount > 0) add("${channelCount}ch")
        if (sampleRate > 0) add("${sampleRate}Hz")
        sampleMimeType?.takeIf { it.isNotBlank() }?.let { add(it) }
    }.joinToDisplayText()

    C.TRACK_TYPE_VIDEO -> buildList {
        if (width > 0 && height > 0) add("${width}x$height")
        sampleMimeType?.takeIf { it.isNotBlank() }?.let { add(it) }
    }.joinToDisplayText()

    C.TRACK_TYPE_TEXT -> buildList {
        label?.takeIf { it.isNotBlank() }?.let { add(it) }
        language?.takeIf { it.isNotBlank() }?.let { add(it) }
        sampleMimeType?.takeIf { it.isNotBlank() }?.let { add(it) }
    }.joinToDisplayText()

    else -> sampleMimeType.orEmpty()
}

private fun List<String>.joinToDisplayText(): String = joinToString(separator = " / ")
