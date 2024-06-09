package com.m3u.feature.crash.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Adb
import com.m3u.material.components.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.m3u.material.components.Background
import java.io.File

@Composable
internal fun FileItem(
    file: File,
    modifier: Modifier = Modifier
) {
    Background(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        ListItem(
            modifier = modifier,
            leadingContent = {
                Icon(
                    imageVector = Icons.Rounded.Adb,
                    contentDescription = file.name
                )
            },
            headlineContent = {
                Text(
                    text = file.nameWithoutExtension,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Text(
                    text = file.path,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        )
    }
}