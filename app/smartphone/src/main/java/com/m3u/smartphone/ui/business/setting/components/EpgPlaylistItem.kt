package com.m3u.smartphone.ui.business.setting.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m3u.data.database.model.Playlist
import com.m3u.smartphone.ui.material.ktx.rememberUiBidiFormatter
import com.m3u.smartphone.ui.material.ktx.safeSourceReference

@Composable
internal fun EpgPlaylistItem(
    epgPlaylist: Playlist,
    onDeleteEpgPlaylist: () -> Unit,
    deleteContentDescription: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val bidiFormatter = rememberUiBidiFormatter()
    val displayTitle = bidiFormatter.natural(epgPlaylist.title)
    val displayReference = epgPlaylist.url.safeSourceReference()
        ?.let(bidiFormatter::ltr)
    val stackAction =
        LocalDensity.current.fontScale >= 1.5f ||
            LocalConfiguration.current.screenWidthDp < 360
    if (stackAction) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        ) {
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleSmall.copy(
                    textDirection = TextDirection.ContentOrLtr,
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            displayReference?.let { reference ->
                Text(
                    text = reference,
                    style = MaterialTheme.typography.bodySmall.copy(
                        textDirection = TextDirection.Ltr,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DeleteButton(
                onClick = onDeleteEpgPlaylist,
                enabled = enabled,
                contentDescription = deleteContentDescription,
                modifier = Modifier.align(Alignment.End),
            )
        }
        return
    }
    ListItem(
        headlineContent = {
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleSmall.copy(
                    textDirection = TextDirection.ContentOrLtr,
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = displayReference?.let { reference ->
            {
                Text(
                    text = reference,
                    style = MaterialTheme.typography.bodySmall.copy(
                        textDirection = TextDirection.Ltr,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingContent = {
            DeleteButton(
                onClick = onDeleteEpgPlaylist,
                enabled = enabled,
                contentDescription = deleteContentDescription,
            )
        },
        modifier = modifier
    )
}

@Composable
private fun DeleteButton(
    onClick: () -> Unit,
    enabled: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(48.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Delete,
            contentDescription = contentDescription,
        )
    }
}
