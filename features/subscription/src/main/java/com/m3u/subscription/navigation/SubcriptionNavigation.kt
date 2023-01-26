package com.m3u.subscription.navigation

import androidx.compose.animation.*
import androidx.navigation.*
import com.google.accompanist.navigation.animation.composable
import com.m3u.subscription.SubscriptionRoute


const val subscriptionRoute = "subscription_route"
private const val subscriptionStringTypeArg = "id"
private const val subscriptionRouteWithArgs = "$subscriptionRoute/{$subscriptionStringTypeArg}"
private fun createSubscriptionRoute(url: String) = "$subscriptionRoute/$url"

fun NavController.navigationToSubscription(url: String, navOptions: NavOptions? = null) {
    val route = createSubscriptionRoute(url.replace("/", "%2F"))
    this.navigate(route, navOptions)
}

@OptIn(ExperimentalAnimationApi::class)
fun NavGraphBuilder.subscriptionScreen(
    navigateToLive: (Int) -> Unit
) {
    composable(
        route = subscriptionRouteWithArgs,
        arguments = listOf(
            navArgument(subscriptionStringTypeArg) {
                type = NavType.StringType
            }
        ),
        enterTransition = { fadeIn(initialAlpha = 1f) },
        exitTransition = { fadeOut(targetAlpha = 0f) },
    ) { navBackStackEntry ->
        val url = navBackStackEntry
            .arguments
            ?.getString(subscriptionStringTypeArg)
            ?.replace("%2F", "/")
            ?: return@composable
        SubscriptionRoute(
            url = url,
            navigateToLive = navigateToLive
        )
    }
}
