package com.m3u.navigation

import androidx.compose.animation.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.m3u.features.live.navigation.liveScreen
import com.m3u.features.main.navgation.mainNavigationRoute
import com.m3u.features.main.navgation.mainScreen
import com.m3u.features.setting.navigation.settingScreen

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun M3UNavHost(
    navController: NavHostController,
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
        popEnterTransition = { fadeIn() },
        popExitTransition = { fadeOut() }
    ) {
        mainScreen()
        liveScreen()
        settingScreen()
    }
}