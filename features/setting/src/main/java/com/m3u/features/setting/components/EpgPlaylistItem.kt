package com.m3u.features.setting.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.m3u.data.database.model.Playlist
import com.m3u.material.components.IconButton

@Composable
internal fun EpgPlaylistItem(
    epgPlaylist: Playlist,
    onDeleteEpgPlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = {
            val text = epgPlaylist.title
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold
            )
        },
        trailingContent = {
            IconButton(
                icon = Icons.Rounded.Delete,
                onClick = onDeleteEpgPlaylist,
                contentDescription = "delete epg"
            )
        },
        modifier = modifier
    )
}