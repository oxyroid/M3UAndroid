package com.m3u.features.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Commit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.util.basic.title
import com.m3u.features.about.components.ContributorItem
import com.m3u.i18n.R.string
import com.m3u.material.components.Background
import com.m3u.material.ktx.plus
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.LocalHelper
import com.m3u.ui.MonoText

@Composable
internal fun AboutRoute(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: AboutViewModel = hiltViewModel()
) {
    val helper = LocalHelper.current
    val title = stringResource(string.feat_about_title)
    LifecycleStartEffect(Unit) {
        helper.title = title.title()
        helper.deep += 1
        onStopOrDispose {
            helper.deep -= 1
        }
    }

    val state by viewModel.s.collectAsStateWithLifecycle()

    AboutScreen(
        contentPadding = contentPadding,
        state = state,
        modifier = modifier.fillMaxSize()
    )
}

@Composable
private fun AboutScreen(
    contentPadding: PaddingValues,
    state: AboutState,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val handler = LocalUriHandler.current
    Background(modifier) {
        val contributors = state.contributors
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
            items(state.libraries) { library ->
                ListItem(
                    headlineContent = {
                        Text(library.key)
                    },
                    supportingContent = {
                        Text(library.group + ":" + library.name)
                    },
                    trailingContent = {
                        MonoText(library.ref)
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.Commit,
                            contentDescription = null
                        )
                    }
                )
                HorizontalDivider()
            }
        }
    }
}
