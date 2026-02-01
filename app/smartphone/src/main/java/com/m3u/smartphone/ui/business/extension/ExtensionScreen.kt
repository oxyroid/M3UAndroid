package com.m3u.smartphone.ui.business.extension

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.m3u.business.extension.App
import com.m3u.business.extension.ExtensionViewModel
import com.m3u.smartphone.ui.common.helper.Metadata

@Composable
fun ExtensionRoute(
    modifier: Modifier = Modifier,
    viewModel: ExtensionViewModel = hiltViewModel(),
    contentPadding: PaddingValues = PaddingValues()
) {
    val apps by viewModel.applications.collectAsStateWithLifecycle()
    ExtensionScreen(
        apps = apps,
        modifier = modifier,
        contentPadding = contentPadding,
        onAppClick = viewModel::runExtension
    )
}

@Composable
private fun ExtensionScreen(
    apps: List<App>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onAppClick: (App) -> Unit,
) {
    LifecycleResumeEffect(Unit) {
        Metadata.title = AnnotatedString("Extensions")
        Metadata.actions = emptyList()
        onPauseOrDispose {  }
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding
    ) {
        items(apps) { app ->
            ListItem(
                headlineContent = {
                    Text(
                        text = app.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                leadingContent = {
                    AsyncImage(
                        model = app.icon,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp)
                    )
                },
                supportingContent = {
                    Text(
                        text = app.description,
                        maxLines = 2
                    )
                },
                trailingContent = {
                    Text(app.version)
                },
                modifier = Modifier.clickable { onAppClick(app) }
            )
            HorizontalDivider()
        }
    }
}