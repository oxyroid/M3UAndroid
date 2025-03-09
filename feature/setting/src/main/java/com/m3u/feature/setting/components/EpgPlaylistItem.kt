package com.m3u.feature.setting.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.m3u.data.database.model.Playlist
import androidx.compose.material3.IconButton

@Composable
internal fun EpgPlaylistItem(
    epgPlaylist: Playlist,
    onDeleteEpgPlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = {
            Text(
                text = epgPlaylist.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = epgPlaylist.url,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = {
            IconButton(
                onClick = onDeleteEpgPlaylist
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "delete epg"
                )
            }
        },
        modifier = modifier
    )
}