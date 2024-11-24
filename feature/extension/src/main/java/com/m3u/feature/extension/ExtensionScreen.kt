package com.m3u.feature.extension

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.extension.runtime.Extension
import com.m3u.feature.extension.components.ExtensionGallery
import com.m3u.i18n.R.string
import com.m3u.material.components.OuterColumn
import com.m3u.ui.helper.Metadata

@Composable
fun ExtensionRoute(
    modifier: Modifier = Modifier,
    viewModel: ExtensionViewModel = hiltViewModel(),
    contentPadding: PaddingValues = PaddingValues()
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
    val label = stringResource(string.feat_extension_label)
    LifecycleResumeEffect(label) {
        Metadata.title = AnnotatedString(label)
        onPauseOrDispose { }
    }
    OuterColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        ExtensionGallery(extensions)
    }
}