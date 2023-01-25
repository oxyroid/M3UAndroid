package com.m3u.subscription.navigation

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.navigation.*
import com.google.accompanist.navigation.animation.composable
import com.m3u.subscription.SubscriptionRoute


const val subscriptionRoute = "subscription_route"
private const val subscriptionIdTypeArg = "id"
private val subscriptionRouteWithArgs = "$subscriptionRoute/{$subscriptionIdTypeArg}"
private fun createSubscriptionRoute(id: Int) = "$subscriptionRoute/$id"

fun NavController.navigationToSubscription(id: Int, navOptions: NavOptions? = null) {
    val route = createSubscriptionRoute(id)
    this.navigate(route, navOptions)
}

@OptIn(ExperimentalAnimationApi::class)
fun NavGraphBuilder.subscriptionScreen(
    navigateToLive: (Int) -> Unit
) {
    composable(
        route = subscriptionRouteWithArgs,
        arguments = listOf(
            navArgument(subscriptionIdTypeArg) {
                type = NavType.IntType
                nullable = false
            }
        )
    ) { navBackStackEntry ->
        val id = navBackStackEntry
            .arguments
            ?.getInt(subscriptionIdTypeArg)
            ?: return@composable
        SubscriptionRoute(
            id = id,
            navigateToLive = navigateToLive
        )
    }
}
