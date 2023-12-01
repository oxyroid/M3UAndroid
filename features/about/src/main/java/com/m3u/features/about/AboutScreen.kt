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
import com.m3u.features.about.model.ContributorHolder
import com.m3u.features.about.model.DependencyHolder
import com.m3u.features.about.model.rememberContributorHolder
import com.m3u.features.about.model.rememberDependencyHolder
import com.m3u.i18n.R.string
import com.m3u.material.components.Background
import com.m3u.material.ktx.plus
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.LocalHelper
import com.m3u.ui.MonoText
import com.m3u.ui.repeatOnLifecycle

@Composable
internal fun AboutRoute(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: AboutViewModel = hiltViewModel()
) {
    val helper = LocalHelper.current
    val title = stringResource(string.feat_about_title)
    helper.repeatOnLifecycle {
        this.title = title
    }

    val contributors by viewModel.contributors.collectAsStateWithLifecycle()
    val dependencies by viewModel.dependencies.collectAsStateWithLifecycle()

    AboutScreen(
        contentPadding = contentPadding,
        contributorHolder = rememberContributorHolder(contributors),
        dependencyHolder = rememberDependencyHolder(dependencies),
        modifier = modifier.fillMaxSize()
    )
}

@Composable
private fun AboutScreen(
    contentPadding: PaddingValues,
    contributorHolder: ContributorHolder,
    dependencyHolder: DependencyHolder,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val handler = LocalUriHandler.current
    Background(modifier) {
        val contributors = contributorHolder.contributions
        val dependencies = dependencyHolder.dependencies
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(spacing.small),
            contentPadding = contentPadding + PaddingValues(horizontal = spacing.medium)
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
                MonoText(
                    text = dependency.name,
                    maxLines = 1
                )
            }
        }
    }
}
