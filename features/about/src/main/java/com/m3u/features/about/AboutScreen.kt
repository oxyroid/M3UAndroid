package com.m3u.features.about

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.m3u.ui.components.Background
import com.m3u.ui.model.LocalHelper
import com.m3u.ui.model.repeatOnLifecycle

@Composable
internal fun AboutRoute(
    modifier: Modifier = Modifier,
) {
    val helper = LocalHelper.current
    val title = stringResource(R.string.about_title)
    helper.repeatOnLifecycle {
        this.title = title
    }
    AboutScreen(
        modifier = modifier.fillMaxSize()
    )
}

@Composable
private fun AboutScreen(
    modifier: Modifier = Modifier
) {
    Background(modifier) {

    }
}