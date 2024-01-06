package com.m3u.features.favorite.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m3u.data.database.entity.Stream
import com.m3u.material.components.Image
import com.m3u.material.model.LocalSpacing

@Composable
internal fun CompatFavoriteItem(
    stream: Stream,
    noPictureMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    zapping: Boolean = false
) {
    val spacing = LocalSpacing.current

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
                AnimatedVisibility(!noPictureMode && !stream.cover.isNullOrEmpty()) {
                    Image(
                        model = stream.cover,
                        errorPlaceholder = stream.title,
                        contentScale = ContentScale.Crop,
                        shape = RoundedCornerShape(spacing.small),
                        modifier = Modifier.size(48.dp)
                    )
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