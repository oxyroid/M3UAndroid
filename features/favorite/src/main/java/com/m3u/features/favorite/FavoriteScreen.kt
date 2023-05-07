package com.m3u.features.favorite

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.features.favorite.components.FavoriteItem
import com.m3u.ui.model.LocalHelper
import com.m3u.ui.model.LocalScalable
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.Scalable
import com.m3u.ui.util.interceptVolumeEvent

typealias NavigateToLive = (Int) -> Unit

@Composable
fun FavouriteRoute(
    navigateToLive: NavigateToLive,
    isCurrentPage: Boolean,
    modifier: Modifier = Modifier,
    viewModel: FavouriteViewModel = hiltViewModel()
) {
    val helper = LocalHelper.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    val rowCount = state.rowCount
    fun onRowCount(target: Int) {
        viewModel.onEvent(FavoriteEvent.SetRowCount(target))
    }
    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            helper.actions = emptyList()
            viewModel.onEvent(FavoriteEvent.InitConfiguration)
        }
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

    CompositionLocalProvider(
        LocalScalable provides Scalable(1f / rowCount)
    ) {
        FavoriteScreen(
            rowCount = rowCount,
            noPictureMode = state.noPictureMode,
            details = state.details,
            navigateToLive = navigateToLive,
            modifier = modifier
                .fillMaxSize()
                .then(interceptVolumeEventModifier)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteScreen(
    rowCount: Int,
    noPictureMode: Boolean,
    details: LiveDetails,
    navigateToLive: NavigateToLive,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val scalable = LocalScalable.current
    val spacing = with(scalable) {
        LocalSpacing.current.scaled
    }

    when (configuration.orientation) {
        Configuration.ORIENTATION_PORTRAIT -> {
            val lives = remember(details) {
                details.flatMap { it.value }
            }
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(rowCount),
                verticalItemSpacing = spacing.medium,
                horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                contentPadding = PaddingValues(LocalSpacing.current.medium),
                modifier = modifier.fillMaxSize()
            ) {
                items(
                    items = lives,
                    contentType = { it.cover.isNullOrEmpty() },
                    key = { it.id }
                ) { live ->
                    FavoriteItem(
                        live = live,
                        noPictureMode = noPictureMode,
                        onClick = {
                            navigateToLive(live.id)
                        },
                        onLongClick = {},
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        Configuration.ORIENTATION_LANDSCAPE -> {
            val lives = remember(details) {
                details.flatMap { it.value }
            }
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(rowCount + 2),
                verticalItemSpacing = spacing.medium,
                horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                contentPadding = PaddingValues(LocalSpacing.current.medium),
                modifier = modifier.fillMaxSize(),
            ) {
                items(
                    items = lives,
                    key = { it.id },
                    contentType = { it.cover.isNullOrEmpty() }
                ) { live ->
                    FavoriteItem(
                        live = live,
                        noPictureMode = noPictureMode,
                        onClick = {
                            navigateToLive(live.id)
                        },
                        onLongClick = {},
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        else -> {}
    }
}