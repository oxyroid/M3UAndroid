package com.m3u.app.navigation

import android.graphics.Rect
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.m3u.features.favorite.navigation.favouriteScreen
import com.m3u.features.feed.navigation.feedScreen
import com.m3u.features.live.navigation.liveScreen
import com.m3u.features.main.navgation.mainNavigationRoute
import com.m3u.features.main.navgation.mainScreen
import com.m3u.features.setting.navigation.settingScreen
import com.m3u.ui.model.SetActions
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun M3UNavHost(
    navController: NavHostController,
    navigateToDestination: (Destination, String) -> Unit,
    setAppActions: SetActions,
    rectSource: MutableStateFlow<Rect>,
    modifier: Modifier = Modifier,
    startDestination: String = mainNavigationRoute
) {
    AnimatedNavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { fadeIn(tween(0)) },
        exitTransition = { fadeOut(tween(0)) },
        popEnterTransition = { fadeIn(tween(0)) },
        popExitTransition = { fadeOut(tween(0)) },
    ) {
        // TopLevel
        mainScreen(
            navigateToFeed = { url, label ->
                navigateToDestination(Destination.Feed(url), label)
            },
            setAppActions = setAppActions
        )
        favouriteScreen(
            navigateToLive = { id ->
                navigateToDestination(Destination.Live(id), "")
            },
            setAppActions = setAppActions
        )
        settingScreen(
            setAppActions = setAppActions
        )

        liveScreen(
            setAppActions = setAppActions,
            rectSource = rectSource
        )

        feedScreen(
            navigateToLive = { id ->
                navigateToDestination(Destination.Live(id), "")
            },
            setAppActions = setAppActions
        )
    }
}