package com.m3u.features.setting.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.m3u.data.database.entity.Live
import com.m3u.material.components.IconButton

@Composable
internal fun MutedLiveItem(
    live: Live,
    onBanned: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = {
            val text = live.title
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            val text = live.url
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            IconButton(
                icon = Icons.Rounded.Close,
                contentDescription = "voice",
                onClick = onBanned
            )
        },
        modifier = modifier
    )
}