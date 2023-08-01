package com.m3u.androidApp.navigation

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

fun NavController.popupToRoot() {
    this.popBackStack(rootNavigationRoute, false)
}

@OptIn(ExperimentalAnimationApi::class)
fun NavGraphBuilder.rootGraph(
    destinations: List<TopLevelDestination>,
    currentPage: Int,
    navigateToFeed: NavigateToFeed,
    navigateToLive: NavigateToLive,
    navigateToConsole: NavigateToConsole,
) {
    composable(
        route = rootNavigationRoute
    ) {
        RootGraph(
            currentPage = currentPage,
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
    destinations: List<TopLevelDestination>,
    navigateToFeed: NavigateToFeed,
    navigateToLive: NavigateToLive,
    navigateToConsole: NavigateToConsole,
    modifier: Modifier = Modifier
) {
    val state = rememberPagerState()
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