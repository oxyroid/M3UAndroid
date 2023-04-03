package com.m3u.features.crash.components

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.ListItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Adb
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.m3u.ui.components.Background
import com.m3u.ui.model.LocalTheme
import java.io.File

@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun FileItem(
    file: File,
    modifier: Modifier = Modifier
) {
    Background(
        color = LocalTheme.current.surface,
        contentColor = LocalTheme.current.onSurface
    ) {
        ListItem(
            modifier = modifier,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Adb,
                    contentDescription = file.name
                )
            },
            text = {
                Text(
                    text = file.nameWithoutExtension,
                    style = MaterialTheme.typography.subtitle1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            secondaryText = {
                Text(
                    text = file.path,
                    style = MaterialTheme.typography.subtitle2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        )
    }
}