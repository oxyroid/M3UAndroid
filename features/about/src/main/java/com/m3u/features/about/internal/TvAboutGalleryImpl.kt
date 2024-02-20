package com.m3u.features.about.internal

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.Text
import com.m3u.material.model.LocalHazeState
import com.mikepenz.aboutlibraries.Libs
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.haze

@Composable
@InternalComposeApi
internal fun TvAboutGalleryImpl(
    libs: Libs,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    TvLazyColumn(
        contentPadding = contentPadding,
        modifier = Modifier
            .fillMaxSize()
            .haze(
                LocalHazeState.current,
                HazeDefaults.style(MaterialTheme.colorScheme.surface)
            )
            .then(modifier)
    ) {
        items(libs.libraries) { library ->
            ListItem(
                selected = false,
                scale = ListItemDefaults.scale(0.96f, 1f),
                onClick = { },
                headlineContent = {
                    Text(library.name)
                },
                supportingContent = {
                    Text(
                        text = library.description.orEmpty(),
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                trailingContent = {
                    Text(library.artifactVersion.orEmpty())
                }
            )
        }
    }
}
