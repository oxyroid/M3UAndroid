package com.m3u.tv.screens.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.SwitchDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.m3u.tv.theme.JetStreamCardShape

@Composable
fun SubtitlesSection(
    isSubtitlesChecked: Boolean,
    onSubtitleCheckChange: (isChecked: Boolean) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 72.dp)) {
        Text(
            text = "SubtitlesSectionTitle",
            style = MaterialTheme.typography.headlineSmall
        )
        ListItem(
            modifier = Modifier.padding(top = 16.dp),
            selected = false,
            onClick = { onSubtitleCheckChange(!isSubtitlesChecked) },
            trailingContent = {
                Switch(
                    checked = isSubtitlesChecked,
                    onCheckedChange = onSubtitleCheckChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primaryContainer,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            },
            headlineContent = {
                Text(
                    text = "SubtitlesSectionSubtitlesItem",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
            ),
            shape = ListItemDefaults.shape(shape = JetStreamCardShape)
        )
        ListItem(
            modifier = Modifier.padding(top = 16.dp),
            selected = false,
            onClick = {},
            trailingContent = {
                Text(
                    text = "SubtitlesSectionLanguageValue",
                    style = MaterialTheme.typography.labelLarge
                )
            },
            headlineContent = {
                Text(
                    text = "SubtitlesSectionLanguageItem",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
            ),
            shape = ListItemDefaults.shape(shape = JetStreamCardShape)
        )
    }
}
