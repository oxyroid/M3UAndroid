package com.m3u.features.crash.components

import androidx.compose.foundation.background
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Adb
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.m3u.ui.model.LocalBackground
import java.io.File

@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun FileItem(
    file: File,
    modifier: Modifier = Modifier,
    asScreen: Boolean = false,
) {
    ListItem(
        modifier = modifier.let {
            if (asScreen) {
                it.background(LocalBackground.current.color)
            } else it
        },
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