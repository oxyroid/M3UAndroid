package com.m3u.features.live.navigation

import androidx.compose.animation.*
import androidx.navigation.*
import com.google.accompanist.navigation.animation.composable
import com.m3u.features.live.LiveRoute
import com.m3u.ui.model.AppAction

const val liveRoute = "live_route"
private const val liveIdTypeArg = "id"
private const val liveRouteWithArgs = "$liveRoute/{$liveIdTypeArg}"
private fun createLiveRoute(id: Int) = "$liveRoute/$id"

fun NavController.navigateToLive(id: Int, navOptions: NavOptions? = null) {
    val route = createLiveRoute(id)
    this.navigate(route, navOptions)
}

@OptIn(ExperimentalAnimationApi::class)
fun NavGraphBuilder.liveScreen(
    setAppActions: (List<AppAction>) -> Unit,
) {
    composable(
        route = liveRouteWithArgs,
        arguments = listOf(
            navArgument(liveIdTypeArg) {
                type = NavType.IntType
                nullable = false
            }
        ),
        enterTransition = {
            fadeIn() + scaleIn(
                initialScale = 0.85f
            )
            slideInVertically { it }
        },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutVertically { it } }
    ) { navBackStackEntry ->
        val id = navBackStackEntry
            .arguments
            ?.getInt(liveIdTypeArg)
            ?: return@composable
        LiveRoute(
            id = id,
            setAppActions = setAppActions
        )
    }
}
