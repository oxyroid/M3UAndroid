package com.m3u.smartphone.ui.business.configuration.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.m3u.i18n.R

@Composable
internal fun AutoSyncProgrammesButton(
    checked: Boolean,
    onCheckedChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = {
            Text(
                text = stringResource(
                    R.string.feat_playlist_configuration_auto_refresh_programmes
                ),
            )
        },
        supportingContent = {
            Text(
                text = stringResource(
                    R.string.feat_playlist_configuration_auto_refresh_programmes_description
                ),
            )
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.DateRange,
                contentDescription = null,
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant,
            leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = { onCheckedChange() },
            )
            .testTag("playlist-configuration-auto-sync"),
    )
}
