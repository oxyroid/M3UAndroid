package com.m3u.smartphone.ui.business.setting.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m3u.data.database.model.Playlist
import com.m3u.smartphone.ui.material.ktx.rememberUiBidiFormatter

@Composable
internal fun HiddenPlaylistGroupItem(
    playlist: Playlist,
    group: String,
    onShow: () -> Unit,
    showLabel: String,
    showContentDescription: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val bidiFormatter = rememberUiBidiFormatter()
    val displayGroup = bidiFormatter.natural(group)
    val displayPlaylistTitle = bidiFormatter.natural(playlist.title)
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
                text = displayGroup,
                style = MaterialTheme.typography.titleSmall.copy(
                    textDirection = TextDirection.ContentOrLtr,
                ),
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = displayPlaylistTitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    textDirection = TextDirection.ContentOrLtr,
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            ShowButton(
                label = showLabel,
                contentDescription = showContentDescription,
                onClick = onShow,
                enabled = enabled,
                modifier = Modifier.align(Alignment.End),
            )
        }
        return
    }

    ListItem(
        headlineContent = {
            Text(
                text = displayGroup,
                style = MaterialTheme.typography.titleSmall.copy(
                    textDirection = TextDirection.ContentOrLtr,
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            Text(
                text = displayPlaylistTitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    textDirection = TextDirection.ContentOrLtr,
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            ShowButton(
                label = showLabel,
                contentDescription = showContentDescription,
                onClick = onShow,
                enabled = enabled,
            )
        },
        modifier = modifier,
    )
}

@Composable
private fun ShowButton(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics {
                this.contentDescription = contentDescription
            },
    ) {
        Text(label)
    }
}
