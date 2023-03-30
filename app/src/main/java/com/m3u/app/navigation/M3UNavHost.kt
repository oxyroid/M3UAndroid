package com.m3u.app.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.m3u.features.console.navigation.consoleScreen
import com.m3u.features.feed.navigation.feedScreen
import com.m3u.features.live.navigation.livePlaylistScreen
import com.m3u.features.live.navigation.liveScreen

@OptIn(ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@Composable
fun M3UNavHost(
    pagerState: PagerState,
    navController: NavHostController,
    destinations: List<TopLevelDestination>,
    navigateToDestination: (Destination) -> Unit,
    modifier: Modifier = Modifier,
    startDestination: String = rootNavigationRoute
) {
    AnimatedNavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { fadeIn(tween(0)) },
        exitTransition = { fadeOut(tween(0)) },
        popEnterTransition = { fadeIn(tween(0)) },
        popExitTransition = { fadeOut(tween(0)) },
    ) {
        rootGraph(
            pagerState = pagerState,
            destinations = destinations,
            navigateToFeed = { url ->
                navigateToDestination(Destination.Feed(url))
            },
            navigateToLive = { id ->
                navigateToDestination(Destination.Live(id))
            },
            navigateToConsole = {
                navigateToDestination(Destination.Console)
            }
        )

        liveScreen()
        livePlaylistScreen()
        feedScreen(
            navigateToLive = { id ->
                navigateToDestination(Destination.Live(id))
            },
            navigateToLivePlayList = { ids, initialIndex ->
                navigateToDestination(Destination.LivePlayList(ids, initialIndex))
            }
        )
        consoleScreen()
    }
}