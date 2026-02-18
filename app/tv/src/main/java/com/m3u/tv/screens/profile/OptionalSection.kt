package com.m3u.tv.screens.profile

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.core.util.basic.title
import com.m3u.i18n.R

@Composable
fun OptionalSection() {
    val preferences = hiltPreferences()
    val size = preferences.playlistItemSize
    val sizeLabel = when (size) {
        0 -> stringResource(R.string.feat_setting_playlist_item_size_large)
        1 -> stringResource(R.string.feat_setting_playlist_item_size_medium)
        2 -> stringResource(R.string.feat_setting_playlist_item_size_small)
        else -> stringResource(R.string.feat_setting_playlist_item_size_compact)
    }.title()

    val skipDetailsPage = preferences.skipDetailsPage
    val skipDetailsLabel = stringResource(
        if (skipDetailsPage) R.string.feat_setting_on else R.string.feat_setting_off
    ).title()

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .padding(horizontal = 72.dp)
            .focusGroup()
            .focusRestorer()
    ) {
        ListItem(
            selected = false,
            headlineContent = {
                Text(
                    text = stringResource(R.string.feat_setting_playlist_item_size).title(),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            trailingContent = {
                Text(
                    text = sizeLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            onClick = {
                preferences.playlistItemSize = (size + 1) % 4
            },
            scale = ListItemDefaults.scale(focusedScale = 1f),
            colors = ListItemDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
            ),
            shape = ListItemDefaults.shape(shape = MaterialTheme.shapes.extraSmall),
            modifier = Modifier.focusRequester(focusRequester).fillMaxWidth()
        )
        ListItem(
            selected = false,
            headlineContent = {
                Text(
                    text = stringResource(R.string.feat_setting_skip_details_page).title(),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            trailingContent = {
                Text(
                    text = skipDetailsLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            onClick = {
                preferences.skipDetailsPage = !preferences.skipDetailsPage
            },
            scale = ListItemDefaults.scale(focusedScale = 1f),
            colors = ListItemDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
            ),
            shape = ListItemDefaults.shape(shape = MaterialTheme.shapes.extraSmall),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
