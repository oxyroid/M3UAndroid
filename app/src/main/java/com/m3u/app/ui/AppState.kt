@file:OptIn(ExperimentalFoundationApi::class)

package com.m3u.app.ui

import android.util.Log
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import com.m3u.app.navigation.Destination
import com.m3u.app.navigation.TopLevelDestination
import com.m3u.app.navigation.popUpToRoot
import com.m3u.app.navigation.rootNavigationRoute
import com.m3u.features.console.navigation.navigateToConsole
import com.m3u.features.feed.navigation.navigationToFeed
import com.m3u.features.live.navigation.navigateToLive
import com.m3u.features.live.navigation.navigateToLivePlayList
import com.m3u.ui.model.AppAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Composable
fun rememberAppState(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    @OptIn(ExperimentalAnimationApi::class)
    navController: NavHostController = rememberAnimatedNavController(),
    pagerState: PagerState = rememberPagerState(),
    title: MutableStateFlow<String> = remember { MutableStateFlow("") },
    actions: MutableStateFlow<List<AppAction>> = remember { MutableStateFlow(emptyList()) },
): AppState {
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            Log.d("AppState", "OnDestinationChanged: $destination")
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }
    return remember(coroutineScope, navController, pagerState, title, actions) {
        AppState(coroutineScope, navController, pagerState, title, actions)
    }
}

@Stable
class AppState(
    private val coroutineScope: CoroutineScope,
    val navController: NavHostController,
    val pagerState: PagerState,
    val title: MutableStateFlow<String>,
    val actions: MutableStateFlow<List<AppAction>>,
) {
    val currentNavDestination: NavDestination?
        @Composable get() = navController.currentBackStackEntryAsState().value?.destination

    val currentTopLevelDestination: TopLevelDestination?
        @Composable get() = when (currentNavDestination?.route) {
            rootNavigationRoute -> topLevelDestinations[pagerState.currentPage]
            else -> null
        }

    val topLevelDestinations: List<TopLevelDestination> = TopLevelDestination.values().asList()

    fun navigateToTopLevelDestination(destination: TopLevelDestination) {
        if (navController.currentBackStackEntry?.destination?.route != rootNavigationRoute) {
            navController.popUpToRoot()
        }
        coroutineScope.launch {
            val target = topLevelDestinations.indexOf(destination)
            pagerState.scrollToPage(target)
        }
    }

    fun navigateToDestination(destination: Destination) {
        when (destination) {
            Destination.Root -> navController.popUpToRoot()
            is Destination.Feed -> navController.navigationToFeed(destination.url)
            is Destination.Live -> navController.navigateToLive(destination.id)
            is Destination.LivePlayList -> navController.navigateToLivePlayList(
                destination.ids,
                destination.initialIndex
            )
            Destination.Console -> navController.navigateToConsole()
        }
    }

    fun onBackClick() {
        navController.popBackStack()
    }
}