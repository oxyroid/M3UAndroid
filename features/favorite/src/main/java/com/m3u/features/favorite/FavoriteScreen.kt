package com.m3u.features.favorite

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.util.context.toast
import com.m3u.features.favorite.components.FavoriteLiveItem
import com.m3u.features.favorite.navigation.NavigateToLive
import com.m3u.ui.model.LocalUtils
import com.m3u.ui.util.EventHandler
import com.m3u.ui.util.LifecycleEffect

@Composable
internal fun FavouriteRoute(
    navigateToLive: NavigateToLive,
    modifier: Modifier = Modifier,
    viewModel: FavouriteViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val utils = LocalUtils.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    EventHandler(state.message) {
        context.toast(it)
    }
    LifecycleEffect { event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                utils.setActions()
            }
            Lifecycle.Event.ON_PAUSE -> {
                utils.setActions()
            }

            else -> {}
        }
    }
    FavoriteScreen(
        lives = state.lives,
        navigateToLive = navigateToLive,
        modifier = modifier
    )
}

@Composable
private fun FavoriteScreen(
    lives: List<LiveDetail>,
    navigateToLive: NavigateToLive,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    when (configuration.orientation) {
        Configuration.ORIENTATION_PORTRAIT -> {
            LazyColumn(
                modifier = modifier.fillMaxSize()
            ) {
                items(
                    items = lives,
                    key = { it.live.id }
                ) { detail ->
                    FavoriteLiveItem(
                        title = detail.title,
                        live = detail.live,
                        onClick = {
                            navigateToLive(detail.live.id)
                        },
                        modifier = Modifier.fillParentMaxWidth()
                    )
                }
            }
        }

        Configuration.ORIENTATION_LANDSCAPE -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = modifier.fillMaxSize()
            ) {
                items(
                    items = lives,
                    key = { it.live.id }
                ) { detail ->
                    FavoriteLiveItem(
                        title = detail.title,
                        live = detail.live,
                        onClick = {
                            navigateToLive(detail.live.id)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        else -> {}
    }
}