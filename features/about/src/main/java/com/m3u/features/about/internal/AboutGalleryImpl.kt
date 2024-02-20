package com.m3u.features.about.internal

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.m3u.material.model.LocalHazeState
import com.mikepenz.aboutlibraries.Libs
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.haze

@Composable
@InternalComposeApi
internal fun AboutGalleryImpl(
    libs: Libs,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    LazyColumn(
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
