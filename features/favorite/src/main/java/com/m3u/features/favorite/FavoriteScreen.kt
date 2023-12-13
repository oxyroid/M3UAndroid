package com.m3u.features.favorite

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.data.database.entity.StreamHolder
import com.m3u.data.database.entity.rememberStreamHolder
import com.m3u.features.favorite.components.FavouriteGallery
import com.m3u.material.ktx.interceptVolumeEvent
import com.m3u.ui.EventHandler
import com.m3u.ui.LocalHelper
import com.m3u.ui.ResumeEvent

typealias NavigateToStream = () -> Unit

@Composable
fun FavouriteRoute(
    navigateToStream: NavigateToStream,
    resume: ResumeEvent,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: FavouriteViewModel = hiltViewModel()
) {
    val helper = LocalHelper.current
    val pref = LocalPref.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val floating by viewModel.floating.collectAsStateWithLifecycle()

    val streams = remember(state.details) {
        state.details.flatMap { it.value }
    }

    EventHandler(resume) {
        helper.actions = emptyList()
    }

    val interceptVolumeEventModifier = remember(pref.godMode) {
        if (pref.godMode) {
            Modifier.interceptVolumeEvent { event ->
                when (event) {
                    KeyEvent.KEYCODE_VOLUME_UP ->
                        pref.rowCount = (pref.rowCount - 1).coerceAtLeast(1)

                    KeyEvent.KEYCODE_VOLUME_DOWN ->
                        pref.rowCount = (pref.rowCount + 1).coerceAtMost(3)
                }
            }
        } else Modifier
    }
    FavoriteScreen(
        contentPadding = contentPadding,
        rowCount = pref.rowCount,
        streamHolder = rememberStreamHolder(
            streams = streams,
            floating = floating
        ),
        navigateToStream = navigateToStream,
        modifier = modifier
            .fillMaxSize()
            .then(interceptVolumeEventModifier)
    )
}

@Composable
private fun FavoriteScreen(
    contentPadding: PaddingValues,
    rowCount: Int,
    streamHolder: StreamHolder,
    navigateToStream: NavigateToStream,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val actualRowCount = when (configuration.orientation) {
        Configuration.ORIENTATION_PORTRAIT -> rowCount
        Configuration.ORIENTATION_LANDSCAPE -> rowCount + 2
        else -> rowCount + 2
    }
    FavouriteGallery(
        contentPadding = contentPadding,
        streamHolder = streamHolder,
        rowCount = actualRowCount,
        navigateToStream = navigateToStream,
        modifier = modifier
    )
}