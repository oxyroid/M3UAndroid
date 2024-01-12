package com.m3u.features.stream.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.m3u.features.stream.model.Format

@Composable
internal fun FormatItem(
    format: Format,
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
                Text("${format.width}×${format.height} ${format.codecs}")
            },
            modifier = modifier.clickable { onClick() }
        )
    }
}