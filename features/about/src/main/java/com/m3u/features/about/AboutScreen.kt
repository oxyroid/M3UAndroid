package com.m3u.features.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.features.about.components.ContributorItem
import com.m3u.features.about.model.Contributor
import com.m3u.ui.components.Background
import com.m3u.ui.components.MonoText
import com.m3u.ui.model.LocalHelper
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.repeatOnLifecycle
import com.m3u.i18n.R as I18R

@Composable
internal fun AboutRoute(
    modifier: Modifier = Modifier,
    viewModel: AboutViewModel = hiltViewModel()
) {
    val helper = LocalHelper.current
    val title = stringResource(I18R.string.feat_about_title)
    helper.repeatOnLifecycle {
        this.title = title
    }

    val contributors by viewModel.contributors.collectAsStateWithLifecycle()
    val dependencies by viewModel.dependencies.collectAsStateWithLifecycle()

    AboutScreen(
        contributors = contributors,
        dependencies = dependencies,
        modifier = modifier.fillMaxSize()
    )
}

@Composable
private fun AboutScreen(
    contributors: List<Contributor>,
    dependencies: List<String>,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val handler = LocalUriHandler.current
    Background(modifier) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(spacing.small),
            contentPadding = PaddingValues(horizontal = spacing.medium)
        ) {
            items(contributors) { contributor ->
                ContributorItem(
                    contributor = contributor,
                    onClick = {
                        handler.openUri(contributor.url)
                    }
                )
            }
            items(dependencies) { dependency ->
                MonoText(dependency, maxLines = 1)
            }
        }
    }
}