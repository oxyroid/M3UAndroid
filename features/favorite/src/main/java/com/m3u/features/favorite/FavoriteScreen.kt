package com.m3u.features.favorite

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.util.context.toast
import com.m3u.features.favorite.components.FavoriteItem
import com.m3u.ui.model.LocalHelper
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.util.EventHandler
import com.m3u.ui.util.RepeatOnCreate

typealias NavigateToLive = (Int) -> Unit

@Composable
fun FavouriteRoute(
    navigateToLive: NavigateToLive,
    modifier: Modifier = Modifier,
    viewModel: FavouriteViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val helper = LocalHelper.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    EventHandler(state.message) {
        context.toast(it)
    }
    RepeatOnCreate {
        helper.actions()
    }
    FavoriteScreen(
        details = state.details,
        navigateToLive = navigateToLive,
        modifier = modifier
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteScreen(
    details: LiveDetails,
    navigateToLive: NavigateToLive,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val spacing = LocalSpacing.current
    when (configuration.orientation) {
        Configuration.ORIENTATION_PORTRAIT -> {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(spacing.medium),
                contentPadding = PaddingValues(spacing.medium),
                modifier = modifier.fillMaxSize()
            ) {
                details.forEach { feed ->
                    item {
                        Text(
                            text = feed.key,
                            style = MaterialTheme.typography.subtitle1
                        )
                    }
                    items(
                        items = feed.value,
                        contentType = { it.cover.isNullOrEmpty() },
                        key = { it.id }
                    ) { live ->
                        FavoriteItem(
                            live = live,
                            onClick = {
                                navigateToLive(live.id)
                            },
                            onLongClick = {},
                            modifier = Modifier.fillParentMaxWidth()
                        )
                    }
                }
            }
        }
        Configuration.ORIENTATION_LANDSCAPE -> {
            val lives = remember(details) {
                details.flatMap { it.value }
            }
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = modifier.fillMaxSize(),
                verticalItemSpacing = spacing.medium,
                horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                contentPadding = PaddingValues(spacing.medium),
            ) {
                items(
                    items = lives,
                    key = { it.id },
                    contentType = { it.cover.isNullOrEmpty() }
                ) { live ->
                    FavoriteItem(
                        live = live,
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