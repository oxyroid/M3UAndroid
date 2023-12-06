package com.m3u.androidApp.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.m3u.core.wrapper.eventOf
import com.m3u.core.wrapper.handledEvent
import com.m3u.features.favorite.FavouriteRoute
import com.m3u.features.favorite.NavigateToLive
import com.m3u.features.main.MainRoute
import com.m3u.features.main.NavigateToFeed
import com.m3u.features.main.NavigateToSettingSubscription
import com.m3u.features.setting.NavigateToAbout
import com.m3u.features.setting.NavigateToConsole
import com.m3u.features.setting.SettingRoute
import com.m3u.material.ktx.Edge
import com.m3u.material.ktx.blurEdge
import com.m3u.material.ktx.log
import com.m3u.ui.Destination
import com.m3u.ui.ResumeEvent
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

const val ROOT_ROUTE = "root_route"

fun NavController.popupToRoot() {
    this.popBackStack(ROOT_ROUTE, false)
}

fun NavGraphBuilder.rootGraph(
    pagerState: PagerState,
    contentPadding: PaddingValues,
    navigateToFeed: NavigateToFeed,
    navigateToLive: NavigateToLive,
    navigateToConsole: NavigateToConsole,
    navigateToAbout: NavigateToAbout,
    navigateToSettingSubscription: NavigateToSettingSubscription,
) {
    composable(ROOT_ROUTE) {
        RootGraph(
            pagerState = pagerState,
            contentPadding = contentPadding,
            navigateToFeed = navigateToFeed,
            navigateToLive = navigateToLive,
            navigateToConsole = navigateToConsole,
            navigateToAbout = navigateToAbout,
            navigateToSettingSubscription = navigateToSettingSubscription
        )
    }
}

@Composable
private fun RootGraph(
    pagerState: PagerState,
    contentPadding: PaddingValues,
    navigateToFeed: NavigateToFeed,
    navigateToLive: NavigateToLive,
    navigateToConsole: NavigateToConsole,
    navigateToAbout: NavigateToAbout,
    navigateToSettingSubscription: NavigateToSettingSubscription,
    modifier: Modifier = Modifier
) {
    val destinations = Destination.Root.entries
    LaunchedEffect(pagerState) {
        snapshotFlow {
            with(pagerState) {
                PagerStateSnapshot(
                    currentPage,
                    targetPage,
                    settledPage,
                    isScrollInProgress
                )
            }
        }
            .onEach { log("pager", it) }
            .launchIn(this)
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxSize()
            .blurEdge(
                edge = Edge.Bottom,
                color = MaterialTheme.colorScheme.background
            )
    ) { pagerIndex ->
        when (destinations[pagerIndex]) {
            Destination.Root.Main -> {
                MainRoute(
                    navigateToFeed = navigateToFeed,
                    navigateToSettingSubscription = navigateToSettingSubscription,
                    resume = rememberResumeEvent(pagerState.targetPage, pagerIndex),
                    contentPadding = contentPadding,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Destination.Root.Favourite -> {
                FavouriteRoute(
                    navigateToLive = navigateToLive,
                    resume = rememberResumeEvent(pagerState.targetPage, pagerIndex),
                    contentPadding = contentPadding,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Destination.Root.Setting -> {
                SettingRoute(
                    navigateToConsole = navigateToConsole,
                    navigateToAbout = navigateToAbout,
                    contentPadding = contentPadding,
                    resume = rememberResumeEvent(pagerState.targetPage, pagerIndex),
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

@Composable
private fun rememberResumeEvent(currentPage: Int, targetPage: Int): ResumeEvent =
    remember(currentPage, targetPage) {
        if (currentPage == targetPage) eventOf(Unit)
        else handledEvent()
    }
