package com.m3u.navigation

import androidx.compose.animation.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.m3u.favorite.navigation.favouriteScreen
import com.m3u.features.live.navigation.liveScreen
import com.m3u.features.main.navgation.mainNavigationRoute
import com.m3u.features.main.navgation.mainScreen
import com.m3u.features.setting.navigation.settingScreen
import com.m3u.subscription.navigation.subscriptionScreen

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun M3UNavHost(
    navController: NavHostController,
    navigateToDestination: (Destination) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    startDestination: String = mainNavigationRoute
) {
    AnimatedNavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
    ) {
        // TopLevel
        mainScreen(
            navigateToSubscription = {
                navigateToDestination(Destination.Subscription(it))
            }
        )
        favouriteScreen()
        settingScreen()

        liveScreen()
        subscriptionScreen(
            navigateToLive = {
                navigateToDestination(Destination.Live(it))
            }
        )
    }
}