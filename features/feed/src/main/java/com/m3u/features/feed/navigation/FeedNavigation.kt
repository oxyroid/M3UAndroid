package com.m3u.features.feed.navigation

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.PaddingValues
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
const val FEED_ROUTE = "$FEED_ROUTE_PATH?$TYPE_URL={$TYPE_URL}"
private fun createFeedRoute(url: String) = "$FEED_ROUTE_PATH?${TYPE_URL}=$url"

fun NavController.navigateToFeed(feedUrl: String, navOptions: NavOptions? = null) {
    val encodedUrl = Uri.encode(feedUrl)
    val route = createFeedRoute(encodedUrl)
    this.navigate(route, navOptions)
}

fun NavGraphBuilder.feedScreen(
    navigateToLive: NavigateToLive,
    navigateToPlaylist: NavigateToPlaylist,
    contentPadding: PaddingValues,
) {
    composable(
        route = FEED_ROUTE,
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
        val feedUrl = navBackStackEntry
            .arguments
            ?.getString(TYPE_URL)
            ?.let(Uri::decode)
            .orEmpty()
        FeedRoute(
            feedUrl = feedUrl,
            navigateToLive = navigateToLive,
            navigateToPlaylist = navigateToPlaylist,
            contentPadding = contentPadding
        )
    }
}
