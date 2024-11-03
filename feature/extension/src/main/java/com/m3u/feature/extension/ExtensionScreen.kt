package com.m3u.feature.extension

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.m3u.extension.runtime.Extension
import com.m3u.feature.extension.components.ExtensionGallery
import com.m3u.material.components.OuterColumn
import com.m3u.material.components.ToggleableSelection
import com.m3u.material.model.LocalSpacing

@Composable
fun ExtensionRoute(
    viewModel: ExtensionViewModel = hiltViewModel(),
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier
) {
    val extensions by viewModel.extensions.collectAsStateWithLifecycle()
    ExtensionScreen(
        extensions = extensions,
        contentPadding = contentPadding,
        modifier = modifier
    )
}

@Composable
private fun ExtensionScreen(
    extensions: List<Extension>,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    OuterColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        ExtensionGallery(extensions)
        LazyColumn {
            items(extensions) { extension ->

            }
        }
    }
}