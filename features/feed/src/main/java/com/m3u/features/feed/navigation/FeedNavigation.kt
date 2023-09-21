package com.m3u.features.feed.navigation

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.m3u.features.feed.FeedRoute
import com.m3u.features.feed.NavigateToLive
import com.m3u.features.feed.NavigateToPlaylist

private const val FEED_ROUTE_PATH = "feed_route"
private const val TYPE_URL = "url"
const val feedRoute = "$FEED_ROUTE_PATH/{$TYPE_URL}"
private fun createFeedRoute(url: String) = "$FEED_ROUTE_PATH/$url"

fun NavController.navigationToFeed(url: String, navOptions: NavOptions? = null) {
    val encodedUrl = Uri.encode(url)
    val route = createFeedRoute(encodedUrl)
    this.navigate(route, navOptions)
}

fun NavGraphBuilder.feedScreen(
    navigateToLive: NavigateToLive,
    navigateToPlayList: NavigateToPlaylist
) {
    composable(
        route = feedRoute,
        arguments = listOf(
            navArgument(TYPE_URL) {
                type = NavType.StringType
            }
        ),
        enterTransition = { slideInVertically { it } },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutVertically { it } }
    ) { navBackStackEntry ->
        val url = navBackStackEntry
            .arguments
            ?.getString(TYPE_URL)
            ?.let(Uri::decode)
            ?: return@composable
        FeedRoute(
            url = url,
            navigateToLive = navigateToLive,
            navigateToPlaylist = navigateToPlayList
        )
    }
}
