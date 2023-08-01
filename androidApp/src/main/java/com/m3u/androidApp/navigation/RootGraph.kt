package com.m3u.androidApp.navigation

import android.util.Log
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import com.google.accompanist.navigation.animation.composable
import com.m3u.features.favorite.FavouriteRoute
import com.m3u.features.favorite.NavigateToLive
import com.m3u.features.main.MainRoute
import com.m3u.features.main.NavigateToFeed
import com.m3u.features.setting.NavigateToConsole
import com.m3u.features.setting.SettingRoute
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

const val rootNavigationRoute = "root_route"

fun NavController.popupToRoot() {
    this.popBackStack(rootNavigationRoute, false)
}

@OptIn(ExperimentalAnimationApi::class)
fun NavGraphBuilder.rootGraph(
    destinations: List<TopLevelDestination>,
    currentPage: Int,
    onCurrentPage: (Int) -> Unit,
    navigateToFeed: NavigateToFeed,
    navigateToLive: NavigateToLive,
    navigateToConsole: NavigateToConsole,
) {
    composable(
        route = rootNavigationRoute
    ) {
        RootGraph(
            currentPage = currentPage,
            onCurrentPage = onCurrentPage,
            destinations = destinations,
            navigateToFeed = navigateToFeed,
            navigateToLive = navigateToLive,
            navigateToConsole = navigateToConsole
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RootGraph(
    currentPage: Int,
    onCurrentPage: (Int) -> Unit,
    destinations: List<TopLevelDestination>,
    navigateToFeed: NavigateToFeed,
    navigateToLive: NavigateToLive,
    navigateToConsole: NavigateToConsole,
    modifier: Modifier = Modifier
) {
    val actualSetCurrentPage by rememberUpdatedState(onCurrentPage)
    val state = rememberPagerState()

    LaunchedEffect(state) {
        snapshotFlow {
            Triple(state.currentPage, state.targetPage, state.settledPage)
        }
            .onEach {
                Log.e("PagerState", "$it")
            }
            .launchIn(this)
    }
    LaunchedEffect(state) {
        snapshotFlow { state.settledPage }
            .onEach(actualSetCurrentPage)
            .launchIn(this)
    }

    LaunchedEffect(currentPage) {
        state.animateScrollToPage(currentPage)
    }
    HorizontalPager(
        state = state,
        pageCount = destinations.size,
        modifier = modifier
    ) { pagerIndex ->
        when (destinations[pagerIndex]) {
            TopLevelDestination.Main -> {
                MainRoute(
                    navigateToFeed = navigateToFeed,
                    isCurrentPage = state.currentPage == pagerIndex,
                    modifier = Modifier.fillMaxSize()
                )
            }

            TopLevelDestination.Favourite -> {
                FavouriteRoute(
                    navigateToLive = navigateToLive,
                    isCurrentPage = state.currentPage == pagerIndex,
                    modifier = Modifier.fillMaxSize()
                )
            }

            TopLevelDestination.Setting -> {
                SettingRoute(
                    navigateToConsole = navigateToConsole,
                    isCurrentPage = state.currentPage == pagerIndex,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}