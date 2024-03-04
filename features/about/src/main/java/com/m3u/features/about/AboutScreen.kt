package com.m3u.features.about

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChangeCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.m3u.core.util.basic.title
import com.m3u.features.about.internal.AboutGalleryImpl
import com.m3u.features.about.internal.TvAboutGalleryImpl
import com.m3u.i18n.R.string
import com.m3u.material.components.Background
import com.m3u.material.ktx.isTelevision
import com.m3u.ui.Destination
import com.m3u.ui.helper.Fob
import com.m3u.ui.helper.LocalHelper
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.util.withContext
import kotlinx.collections.immutable.persistentListOf

@Composable
internal fun AboutRoute(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val helper = LocalHelper.current
    val onBackPressedDispatcher = checkNotNull(
        LocalOnBackPressedDispatcherOwner.current
    ).onBackPressedDispatcher

    val title = stringResource(string.feat_about_title)

    LifecycleResumeEffect(title) {
        helper.title = title.title()
        helper.actions = persistentListOf()
        helper.fob = Fob(
            rootDestination = Destination.Root.Setting,
            icon = Icons.Rounded.ChangeCircle,
            iconTextId = string.feat_setting_back_home
        ) {
            onBackPressedDispatcher.onBackPressed()
        }
        onPauseOrDispose {
            helper.fob = null
        }
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
