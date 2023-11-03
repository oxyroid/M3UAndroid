package com.m3u.features.live.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navOptions
import com.m3u.core.util.transform.IntIterativeTransferable
import com.m3u.features.live.LiveEvent
import com.m3u.features.live.LiveRoute

private const val LIVE_ROUTE_PATH = "live_route"
private const val LIVE_PLAYLIST_ROUTE_PATH = "live_playlist_route"

private const val TYPE_ID = "id"
const val LIVE_ROUTE = "$LIVE_ROUTE_PATH/{$TYPE_ID}"
private fun createLiveRoute(id: Int) = "$LIVE_ROUTE_PATH/$id"

private const val TYPE_INITIAL_INDEX = "index"
private const val TYPE_IDS = "ids"
const val LIVE_PLAYLIST_ROUTE = "$LIVE_PLAYLIST_ROUTE_PATH/{$TYPE_IDS}/{$TYPE_INITIAL_INDEX}"

private fun createLivePlaylistRoute(ids: List<Int>, initialIndex: Int) =
    "$LIVE_PLAYLIST_ROUTE_PATH/${IntIterativeTransferable.transfer(ids)}/$initialIndex"

fun NavController.navigateToLive(id: Int) {
    val navOptions = navOptions {
        launchSingleTop = true
    }
    val route = createLiveRoute(id)
    this.navigate(route, navOptions)
}

fun NavController.navigateToPlaylist(ids: List<Int>, initialIndex: Int) {
    val navOptions = navOptions {
        launchSingleTop = true
    }
    val route = createLivePlaylistRoute(ids, initialIndex)
    this.navigate(route, navOptions)
}

fun NavGraphBuilder.liveScreen(
    onBackPressed: () -> Unit
) {
    composable(
        route = LIVE_ROUTE,
        arguments = listOf(
            navArgument(TYPE_ID) {
                type = NavType.IntType
                nullable = false
            },
        ),
        enterTransition = { slideInVertically { it } + fadeIn() },
        exitTransition = { slideOutVertically { it } + fadeOut() }
    ) { navBackStackEntry ->
        val id = navBackStackEntry
            .arguments
            ?.getInt(TYPE_ID)
            ?: return@composable
        LiveRoute(
            init = LiveEvent.InitSpecial(id),
            onBackPressed = onBackPressed
        )
    }
}

fun NavGraphBuilder.livePlaylistScreen(
    onBackPressed: () -> Unit
) {
    composable(
        route = LIVE_PLAYLIST_ROUTE,
        arguments = listOf(
            navArgument(TYPE_IDS) {
                type = NavType.StringType
                nullable = false
            },
            navArgument(TYPE_INITIAL_INDEX) {
                type = NavType.IntType
                nullable = false
            },
        ),
        enterTransition = { slideInVertically { it } },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutVertically { it } }
    ) { navBackStackEntry ->
        val ids = navBackStackEntry
            .arguments
            ?.getString(TYPE_IDS)
            ?.let(IntIterativeTransferable::accept)
            ?.toList()
            ?: return@composable
        val initialIndex = navBackStackEntry
            .arguments
            ?.getInt(TYPE_INITIAL_INDEX)
            ?: return@composable
        LiveRoute(
            init = LiveEvent.InitPlayList(
                initialIndex = initialIndex,
                ids = ids
            ),
            onBackPressed = onBackPressed
        )
    }
}