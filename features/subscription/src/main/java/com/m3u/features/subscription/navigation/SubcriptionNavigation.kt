package com.m3u.features.subscription.navigation

import android.net.Uri
import androidx.compose.animation.*
import androidx.navigation.*
import com.google.accompanist.navigation.animation.composable
import com.m3u.features.subscription.SubscriptionRoute
import com.m3u.ui.model.SetActions


const val subscriptionRoute = "subscription_route"
private const val subscriptionStringTypeArg = "id"
private const val subscriptionRouteWithArgs = "$subscriptionRoute/{$subscriptionStringTypeArg}"
private fun createSubscriptionRoute(url: String) = "$subscriptionRoute/$url"

fun NavController.navigationToSubscription(url: String, navOptions: NavOptions? = null) {
    val route = createSubscriptionRoute(Uri.encode(url))
    this.navigate(route, navOptions)
}

@OptIn(ExperimentalAnimationApi::class)
fun NavGraphBuilder.subscriptionScreen(
    navigateToLive: (Int) -> Unit,
    setAppActions: SetActions
) {
    composable(
        route = subscriptionRouteWithArgs,
        arguments = listOf(
            navArgument(subscriptionStringTypeArg) {
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
            ?.getString(subscriptionStringTypeArg)
            ?.let(Uri::decode)
            ?: return@composable
        SubscriptionRoute(
            url = url,
            navigateToLive = navigateToLive,
            setAppActions = setAppActions
        )
    }
}
