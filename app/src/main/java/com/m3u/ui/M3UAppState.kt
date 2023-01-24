package com.m3u.ui

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.*
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import com.m3u.features.setting.navigation.settingNavigationRoute
import com.m3u.features.setting.navigation.navigateToSetting
import com.m3u.features.live.navigation.liveNavigationRoute
import com.m3u.features.live.navigation.navigateToLive
import com.m3u.features.main.navgation.mainNavigationRoute
import com.m3u.features.main.navgation.navigateToMain
import com.m3u.navigation.TopLevelDestination
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun rememberM3UAppState(
    navController: NavHostController = rememberAnimatedNavController(),
    coroutineScope: CoroutineScope = rememberCoroutineScope()
): M3UAppState {
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->

        }
        navController.addOnDestinationChangedListener(listener)
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }
    return remember(navController, coroutineScope) {
        M3UAppState(navController, coroutineScope)
    }
}

@Stable
class M3UAppState(
    val navController: NavHostController,
    val coroutineScope: CoroutineScope
) {
    val currentDestination: NavDestination?
        @Composable get() = navController
            .currentBackStackEntryAsState().value?.destination

    val currentTopLevelDestination: TopLevelDestination?
        @Composable get() = when (currentDestination?.route) {
            mainNavigationRoute -> TopLevelDestination.MAIN
            liveNavigationRoute -> TopLevelDestination.LIVE
            settingNavigationRoute -> TopLevelDestination.SETTING
            else -> null
        }

    val topLevelDestinations: List<TopLevelDestination> = TopLevelDestination.values().asList()

    @Throws(IllegalArgumentException::class)
    fun navigateToTopLevelDestination(topLevelDestination: TopLevelDestination) {
        val topLevelNavOptions = navOptions {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
        when (topLevelDestination) {
            TopLevelDestination.MAIN -> navController.navigateToMain(topLevelNavOptions)
            TopLevelDestination.LIVE -> navController.navigateToLive(topLevelNavOptions)
            TopLevelDestination.SETTING -> navController.navigateToSetting(topLevelNavOptions)
        }
    }

    fun onBackClick() {
        navController.popBackStack()
    }
}