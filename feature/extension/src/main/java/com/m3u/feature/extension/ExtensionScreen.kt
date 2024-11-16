package com.m3u.feature.extension

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.extension.runtime.Extension
import com.m3u.feature.extension.components.ExtensionGallery
import com.m3u.material.components.OuterColumn

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
    }
}