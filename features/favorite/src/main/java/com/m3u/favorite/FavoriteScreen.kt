package com.m3u.favorite

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.util.toast
import com.m3u.favorite.components.FavoriteLiveItem
import com.m3u.favorite.vo.LiveWithTitle
import com.m3u.ui.util.EventEffect

@Composable
internal fun FavouriteRoute(
    modifier: Modifier = Modifier,
    viewModel: FavouriteViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.readable.collectAsStateWithLifecycle()
    EventEffect(state.message) {
        context.toast(it)
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