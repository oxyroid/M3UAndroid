package com.m3u.favorite

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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.util.toast
import com.m3u.favorite.components.FavoriteLiveItem
import com.m3u.favorite.vo.LiveWithTitle
import com.m3u.ui.model.AppAction
import com.m3u.ui.model.SetActions
import com.m3u.ui.util.EventHandler
import com.m3u.ui.util.LifecycleEffect

@Composable
internal fun FavouriteRoute(
    modifier: Modifier = Modifier,
    viewModel: FavouriteViewModel = hiltViewModel(),
    setAppActions: SetActions
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    EventHandler(state.message) {
        context.toast(it)
    }
    val setAppActionsUpdated by rememberUpdatedState(setAppActions)
    LifecycleEffect { event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                val actions = listOf<AppAction>()
                setAppActionsUpdated(actions)
            }

            Lifecycle.Event.ON_PAUSE -> {
                setAppActionsUpdated(emptyList())
            }

            else -> {}
        }
    }
    FavoriteScreen(
        lives = state.lives,
        modifier = modifier
    )
}

@Composable
private fun FavoriteScreen(
    lives: List<LiveWithTitle>,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    when (configuration.orientation) {
        Configuration.ORIENTATION_PORTRAIT -> {
            LazyColumn(
                modifier = modifier.fillMaxSize()
            ) {
                items(lives) { liveWithTitle ->
                    FavoriteLiveItem(
                        live = liveWithTitle.live,
                        subscriptionTitle = liveWithTitle.title,
                        onClick = {

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
                items(lives) { liveWithTitle ->
                    FavoriteLiveItem(
                        live = liveWithTitle.live,
                        subscriptionTitle = liveWithTitle.title,
                        onClick = {

                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        else -> {}
    }
}