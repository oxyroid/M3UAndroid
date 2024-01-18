package com.m3u.features.about

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleStartEffect
import com.m3u.core.util.basic.title
import com.m3u.i18n.R.string
import com.m3u.material.components.Background
import com.m3u.ui.LocalHelper
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer

@Composable
internal fun AboutRoute(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    // viewModel: AboutViewModel = hiltViewModel()
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

    AboutScreen(
        contentPadding = contentPadding,
        modifier = modifier.fillMaxSize()
    )
}

@Composable
private fun AboutScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    Background(modifier) {
        LibrariesContainer(
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize()
        )
    }
}
