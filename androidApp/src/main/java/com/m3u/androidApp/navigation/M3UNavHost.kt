package com.m3u.androidApp.navigation

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.m3u.androidApp.ui.NavigateToDestination
import com.m3u.features.console.navigation.consoleScreen
import com.m3u.features.feed.navigation.feedScreen
import com.m3u.features.live.navigation.livePlaylistScreen
import com.m3u.features.live.navigation.liveScreen
import com.m3u.features.main.R
import com.m3u.ui.model.LocalHelper

@OptIn(ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@Composable
fun M3UNavHost(
    pagerState: PagerState,
    navController: NavHostController,
    destinations: List<TopLevelDestination>,
    navigateToDestination: NavigateToDestination,
    modifier: Modifier = Modifier,
    startDestination: String = rootNavigationRoute
) {
    val helper = LocalHelper.current
    val context = LocalContext.current
    AnimatedNavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn(tween(0)) },
        exitTransition = { fadeOut(tween(0)) },
        popEnterTransition = { fadeIn(tween(0)) },
        popExitTransition = { fadeOut(tween(0)) },
        modifier = modifier,
    ) {
        rootGraph(
            pagerState = pagerState,
            destinations = destinations,
            navigateToFeed = { feed ->
                helper.title = if (!feed.isTemplated()) feed.title
                else context.getString(R.string.imported_feed_title)
                navigateToDestination(Destination.Feed(feed.url))
            },
            navigateToLive = { id ->
                navigateToDestination(Destination.Live(id))
            },
            navigateToConsole = {
                navigateToDestination(Destination.Console)
            }
        )

        liveScreen(
            onBackPressed = {
                navController.popBackStack()
            }
        )
        livePlaylistScreen(
            onBackPressed = {
                navController.popBackStack()
            }
        )
        feedScreen(
            navigateToLive = { id ->
                navigateToDestination(Destination.Live(id))
            },
            navigateToPlayList = { ids, initial ->
                navigateToDestination(Destination.LivePlayList(ids, initial))
            }
        )
        consoleScreen()
    }
}
