package com.m3u.androidApp.navigation

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
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

const val rootNavigationRoute = "root_route"


fun NavController.popUpToRoot() {
    this.popBackStack(rootNavigationRoute, false)
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
fun NavGraphBuilder.rootGraph(
    pagerState: PagerState,
    destinations: List<TopLevelDestination>,
    navigateToFeed: NavigateToFeed,
    navigateToLive: NavigateToLive,
    navigateToConsole: NavigateToConsole,
) {
    composable(
        route = rootNavigationRoute
    ) {
        RootGraph(
            state = pagerState,
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
    state: PagerState,
    destinations: List<TopLevelDestination>,
    navigateToFeed: NavigateToFeed,
    navigateToLive: NavigateToLive,
    navigateToConsole: NavigateToConsole,
    modifier: Modifier = Modifier
) {
    HorizontalPager(
        modifier = modifier,
        state = state,
        pageCount = destinations.size
    ) { pagerIndex ->
        when (destinations[pagerIndex]) {
            TopLevelDestination.Main -> {
                MainRoute(
                    navigateToFeed = navigateToFeed,
                    modifier = Modifier.fillMaxSize()
                )
            }
            TopLevelDestination.Favourite -> {
                FavouriteRoute(
                    navigateToLive = navigateToLive,
                    modifier = Modifier.fillMaxSize()
                )
            }
            TopLevelDestination.Setting -> {
                SettingRoute(
                    navigateToConsole = navigateToConsole,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}