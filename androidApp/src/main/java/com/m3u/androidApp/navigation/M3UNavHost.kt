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
import com.m3u.features.about.navigation.aboutScreen
import com.m3u.features.console.navigation.consoleScreen
import com.m3u.features.feed.navigation.feedScreen
import com.m3u.features.live.navigation.livePlaylistScreen
import com.m3u.features.live.navigation.liveScreen
import com.m3u.i18n.R.string
import com.m3u.ui.Destination
import com.m3u.ui.LocalHelper
import com.m3u.ui.Navigate

@Composable
fun M3UNavHost(
    navController: NavHostController,
    currentPage: Int,
    onCurrentPage: (Int) -> Unit,
    navigate: Navigate,
    modifier: Modifier = Modifier,
    startDestination: String = ROOT_ROUTE
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
            currentPage = currentPage,
            onCurrentPage = onCurrentPage,
            navigateToFeed = { feed ->
                helper.title = feed.title.ifEmpty {
                    if (feed.local) context.getString(string.feat_main_imported_feed_title)
                    else ""
                }
                navigate(Destination.Feed(feed.url))
            },
            navigateToLive = { id ->
                navigate(Destination.Live(id))
            },
            navigateToConsole = {
                navigate(Destination.Console)
            },
            navigateToAbout = {
                navigate(Destination.About)
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
                navigate(Destination.Live(id))
            },
            navigateToPlayList = { ids, initial ->
                navigate(Destination.LivePlayList(ids, initial))
            }
        )
        consoleScreen()
        aboutScreen()
    }
}
