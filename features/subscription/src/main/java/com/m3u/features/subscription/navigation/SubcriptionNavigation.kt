package com.m3u.features.subscription.navigation

import android.net.Uri
import androidx.compose.animation.*
import androidx.navigation.*
import com.google.accompanist.navigation.animation.composable
import com.m3u.features.subscription.SubscriptionRoute
import com.m3u.ui.model.SetActions


const val subscriptionRoute = "subscription_route"
private const val TYPE_URL = "url"
private const val ROUTE_PLACEHOLDER = "$subscriptionRoute/{$TYPE_URL}"
private fun createSubscriptionRoute(url: String) = "$subscriptionRoute/$url"

fun NavController.navigationToSubscription(url: String, navOptions: NavOptions? = null) {
    val encodedUrl = Uri.encode(url)
    val route = createSubscriptionRoute(encodedUrl)
    this.navigate(route, navOptions)
}

@OptIn(ExperimentalAnimationApi::class)
fun NavGraphBuilder.subscriptionScreen(
    navigateToLive: (Int) -> Unit,
    setAppActions: SetActions
) {
    composable(
        route = ROUTE_PLACEHOLDER,
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
        SubscriptionRoute(
            url = url,
            navigateToLive = navigateToLive,
            setAppActions = setAppActions
        )
    }
}
