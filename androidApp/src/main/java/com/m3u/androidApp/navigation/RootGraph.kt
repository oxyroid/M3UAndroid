package com.m3u.androidApp.navigation

import android.util.Log
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
import androidx.navigation.compose.composable
import com.m3u.features.favorite.FavouriteRoute
import com.m3u.features.favorite.NavigateToLive
import com.m3u.features.main.MainRoute
import com.m3u.features.main.NavigateToFeed
import com.m3u.features.setting.NavigateToAbout
import com.m3u.features.setting.NavigateToConsole
import com.m3u.features.setting.SettingRoute
import com.m3u.ui.Destination
import com.m3u.ui.ktx.Edge
import com.m3u.ui.ktx.blurEdges
import com.m3u.ui.model.LocalTheme
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

const val rootRoute = "root_route"

fun NavController.popupToRoot() {
    this.popBackStack(rootRoute, false)
}

fun NavGraphBuilder.rootGraph(
    currentPage: Int,
    onCurrentPage: (Int) -> Unit,
    navigateToFeed: NavigateToFeed,
    navigateToLive: NavigateToLive,
    navigateToConsole: NavigateToConsole,
    navigateToAbout: NavigateToAbout,
) {
    composable(rootRoute) {
        RootGraph(
            currentPage = currentPage,
            onCurrentPage = onCurrentPage,
            navigateToFeed = navigateToFeed,
            navigateToLive = navigateToLive,
            navigateToConsole = navigateToConsole,
            navigateToAbout = navigateToAbout
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RootGraph(
    currentPage: Int,
    onCurrentPage: (Int) -> Unit,
    navigateToFeed: NavigateToFeed,
    navigateToLive: NavigateToLive,
    navigateToConsole: NavigateToConsole,
    navigateToAbout: NavigateToAbout,
    modifier: Modifier = Modifier
) {
    val destinations = Destination.Root.entries
    val pagerState = rememberPagerState { destinations.size }
    val actualOnCurrentPage by rememberUpdatedState(onCurrentPage)

    LaunchedEffect(pagerState) {
        snapshotFlow {
            PagerStateSnapshot(
                pagerState.currentPage,
                pagerState.targetPage,
                pagerState.settledPage,
                pagerState.isScrollInProgress
            )
        }
            .onEach {
                Log.e("PagerState", "$it")
            }
            .launchIn(this)

        snapshotFlow {
            // FIXME:
            //  When a user scrolls the page using gestures on the root screen, it may be 0.
            //  But we cannot use pageState#currentPage because it will not work
            //  when we selects a bottom bar item from 0 -> 2
            pagerState.targetPage
        }
            .onEach(actualOnCurrentPage)
            .launchIn(this)
    }

    LaunchedEffect(currentPage) {
        if (currentPage != pagerState.currentPage) {
            pagerState.animateScrollToPage(currentPage)
        }
    }
    HorizontalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxSize()
            .blurEdges(
                edges = listOf(Edge.Top, Edge.Bottom),
                color = LocalTheme.current.background
            )
    ) { pagerIndex ->
        when (destinations[pagerIndex]) {
            Destination.Root.Main -> {
                MainRoute(
                    navigateToFeed = navigateToFeed,
                    isCurrentPage = currentPage == pagerIndex,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Destination.Root.Favourite -> {
                FavouriteRoute(
                    navigateToLive = navigateToLive,
                    isCurrentPage = currentPage == pagerIndex,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Destination.Root.Setting -> {
                SettingRoute(
                    navigateToConsole = navigateToConsole,
                    navigateToAbout = navigateToAbout,
                    isCurrentPage = currentPage == pagerIndex,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private data class PagerStateSnapshot(
    val current: Int,
    val target: Int,
    val settled: Int,
    val scrolling: Boolean
)