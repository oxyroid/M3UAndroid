package com.m3u.feature.extension.components

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.m3u.extension.runtime.Extension

@Composable
internal fun ExtensionGallery(
    extensions: List<Extension>,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier) {
        items(extensions) { extension ->
            ExtensionGalleryItem(extension)
        }
    }
}