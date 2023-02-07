package com.m3u.features.live.navigation

import androidx.compose.animation.*
import androidx.navigation.*
import com.google.accompanist.navigation.animation.composable
import com.m3u.features.live.LiveRoute
import com.m3u.ui.model.SetActions

const val liveRoute = "live_route"
private const val TYPE_ID = "id"
private const val ROUTE_PLACEHOLDER = "$liveRoute/{$TYPE_ID}"
private fun createLiveRoute(id: Int) = "$liveRoute/$id"

fun NavController.navigateToLive(id: Int) {
    val navOptions = navOptions {
        launchSingleTop = true
    }
    val route = createLiveRoute(id)
    this.navigate(route, navOptions)
}

@OptIn(ExperimentalAnimationApi::class)
fun NavGraphBuilder.liveScreen(
    setAppActions: SetActions
) {
    composable(
        route = ROUTE_PLACEHOLDER,
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
        LiveRoute(
            id = id,
            setAppActions = setAppActions
        )
    }
}
