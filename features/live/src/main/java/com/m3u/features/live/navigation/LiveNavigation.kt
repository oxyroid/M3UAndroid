package com.m3u.features.live.navigation

import androidx.compose.animation.*
import androidx.navigation.*
import com.google.accompanist.navigation.animation.composable
import com.m3u.features.live.LiveRoute

private const val LIVE_ROUTE_PATH = "live_route"
private const val TYPE_ID = "id"
const val liveRoute = "$LIVE_ROUTE_PATH/{$TYPE_ID}"
private fun createLiveRoute(id: Int) = "$LIVE_ROUTE_PATH/$id"

fun NavController.navigateToLive(id: Int) {
    val navOptions = navOptions {
        launchSingleTop = true
    }
    val route = createLiveRoute(id)
    this.navigate(route, navOptions)
}

@OptIn(ExperimentalAnimationApi::class)
fun NavGraphBuilder.liveScreen() {
    composable(
        route = liveRoute,
        arguments = listOf(
            navArgument(TYPE_ID) {
                type = NavType.IntType
                nullable = false
            }
        ),
        enterTransition = { slideInVertically { it } },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutVertically { it } }
    ) { navBackStackEntry ->
        val id = navBackStackEntry
            .arguments
            ?.getInt(TYPE_ID)
            ?: return@composable
        LiveRoute(id = id)
    }
}
