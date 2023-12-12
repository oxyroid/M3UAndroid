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
import com.m3u.data.database.entity.LiveHolder
import com.m3u.data.database.entity.rememberLiveHolder
import com.m3u.features.favorite.components.FavouriteGallery
import com.m3u.material.ktx.interceptVolumeEvent
import com.m3u.ui.EventHandler
import com.m3u.ui.LocalHelper
import com.m3u.ui.ResumeEvent

typealias NavigateToLive = () -> Unit

@Composable
fun FavouriteRoute(
    navigateToLive: NavigateToLive,
    resume: ResumeEvent,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: FavouriteViewModel = hiltViewModel()
) {
    val helper = LocalHelper.current
    val pref = LocalPref.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    val lives = remember(state.details) {
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
        liveHolder = rememberLiveHolder(lives),
        navigateToLive = navigateToLive,
        modifier = modifier
            .fillMaxSize()
            .then(interceptVolumeEventModifier)
    )
}

@Composable
private fun FavoriteScreen(
    contentPadding: PaddingValues,
    rowCount: Int,
    liveHolder: LiveHolder,
    navigateToLive: NavigateToLive,
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
        liveHolder = liveHolder,
        rowCount = actualRowCount,
        navigateToLive = navigateToLive,
        modifier = modifier
    )
}