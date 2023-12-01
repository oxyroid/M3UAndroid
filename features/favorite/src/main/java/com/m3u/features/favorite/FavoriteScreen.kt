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
import com.m3u.data.database.entity.LiveHolder
import com.m3u.data.database.entity.rememberLiveHolder
import com.m3u.features.favorite.components.FavouriteGallery
import com.m3u.material.ktx.interceptVolumeEvent
import com.m3u.ui.EventHandler
import com.m3u.ui.LocalHelper
import com.m3u.ui.ResumeEvent

typealias NavigateToLive = (Int) -> Unit

@Composable
fun FavouriteRoute(
    navigateToLive: NavigateToLive,
    resume: ResumeEvent,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: FavouriteViewModel = hiltViewModel()
) {
    val helper = LocalHelper.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    val lives = remember(state.details) {
        state.details.flatMap { it.value }
    }

    val rowCount = state.rowCount
    fun onRowCount(target: Int) {
        state.rowCount = target
    }
    EventHandler(resume) {
        helper.actions = emptyList()
    }

    val interceptVolumeEventModifier = remember(state.godMode) {
        if (state.godMode) {
            Modifier.interceptVolumeEvent { event ->
                when (event) {
                    KeyEvent.KEYCODE_VOLUME_UP ->
                        onRowCount((rowCount - 1).coerceAtLeast(1))

                    KeyEvent.KEYCODE_VOLUME_DOWN ->
                        onRowCount((rowCount + 1).coerceAtMost(3))
                }
            }
        } else Modifier
    }
    FavoriteScreen(
        contentPadding = contentPadding,
        rowCount = rowCount,
        noPictureMode = state.noPictureMode,
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
    noPictureMode: Boolean,
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
        noPictureMode = noPictureMode,
        navigateToLive = navigateToLive,
        modifier = modifier
    )
}