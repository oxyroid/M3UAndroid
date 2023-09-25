package com.m3u.androidApp.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.m3u.androidApp.ui.NavigateToDestination
import com.m3u.features.about.navigation.aboutScreen
import com.m3u.features.console.navigation.consoleScreen
import com.m3u.features.feed.navigation.feedScreen
import com.m3u.features.live.navigation.livePlaylistScreen
import com.m3u.features.live.navigation.liveScreen
import com.m3u.features.main.R
import com.m3u.ui.TopLevelDestination
import com.m3u.ui.model.LocalHelper

@Composable
fun M3UNavHost(
    navController: NavHostController,
    currentPage: Int,
    onCurrentPage: (Int) -> Unit,
    destinations: List<TopLevelDestination>,
    navigateToDestination: NavigateToDestination,
    modifier: Modifier = Modifier,
    startDestination: String = rootNavigationRoute
) {
    val helper = LocalHelper.current
    val context = LocalContext.current
    NavHost(
        navController = navController,
        startDestination = startDestination,
        exitTransition = { slideOutVertically { -it / 5 } + fadeOut() },
        popEnterTransition = { slideInVertically { -it / 5 } + fadeIn() },
        modifier = modifier,
    ) {
        rootGraph(
            destinations = destinations,
            currentPage = currentPage,
            onCurrentPage = onCurrentPage,
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
            },
            navigateToAbout = {
                navigateToDestination(Destination.About)
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
        aboutScreen()
    }
}
