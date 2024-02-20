package com.m3u.features.about

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.m3u.core.util.basic.title
import com.m3u.features.about.internal.AboutGalleryImpl
import com.m3u.features.about.internal.TvAboutGalleryImpl
import com.m3u.i18n.R.string
import com.m3u.material.components.Background
import com.m3u.material.ktx.isTelevision
import com.m3u.ui.helper.LocalHelper
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.util.withContext

@Composable
internal fun AboutRoute(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val helper = LocalHelper.current
    val title = stringResource(string.feat_about_title)
    LaunchedEffect(title) {
        helper.title = title.title()
    }

    AboutScreen(
        contentPadding = contentPadding,
        modifier = modifier.fillMaxSize()
    )
}

@OptIn(InternalComposeApi::class)
@Composable
private fun AboutScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tv = isTelevision()

    val libs = remember(context) {
        Libs.Builder().withContext(context).build()
    }

    Background {
        if (!tv) {
            AboutGalleryImpl(
                libs = libs,
                contentPadding = contentPadding,
                modifier = modifier
            )
        } else {
            TvAboutGalleryImpl(
                libs = libs,
                contentPadding = contentPadding,
                modifier = modifier
            )
        }
    }
}
