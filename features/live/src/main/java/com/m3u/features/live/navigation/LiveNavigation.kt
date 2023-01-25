package com.m3u.features.live.navigation

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.navigation.*
import com.google.accompanist.navigation.animation.composable
import com.m3u.features.live.LiveRoute

const val liveRoute = "live_route"
private const val liveIdTypeArg = "id"
private val liveRouteWithArgs = "$liveRoute/{$liveIdTypeArg}"
private fun createLiveRoute(id: Int) = "$liveRoute/$id"

fun NavController.navigateToLive(id: Int, navOptions: NavOptions? = null) {
    val route = createLiveRoute(id)
    this.navigate(route, navOptions)
}

@OptIn(ExperimentalAnimationApi::class)
fun NavGraphBuilder.liveScreen() {
    composable(
        route = "$liveRoute/{$liveIdTypeArg}",
        arguments = listOf(
            navArgument(liveIdTypeArg) {
                type = NavType.IntType
                nullable = false
            }
        )
    ) { navBackStackEntry ->
        val id = navBackStackEntry
            .arguments
            ?.getInt(liveIdTypeArg)
            ?: return@composable
        LiveRoute(id = id)
    }
}
