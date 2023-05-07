@file:OptIn(ExperimentalFoundationApi::class)

package com.m3u.androidApp.ui

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.m3u.androidApp.navigation.Destination
import com.m3u.androidApp.navigation.TopLevelDestination
import com.m3u.androidApp.navigation.notDestinationTo
import com.m3u.androidApp.navigation.popUpToRoot
import com.m3u.androidApp.navigation.rootNavigationRoute
import com.m3u.features.console.navigation.navigateToConsole
import com.m3u.features.feed.navigation.navigationToFeed
import com.m3u.features.live.navigation.navigateToLive
import com.m3u.features.live.navigation.navigateToLivePlayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

typealias NavigateToDestination = (destination: Destination) -> Unit

@Composable
fun rememberAppState(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    navController: NavHostController = rememberNavController(),
    pagerState: PagerState = rememberPagerState()
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
    LaunchedEffect(pagerState) {
        snapshotFlow {
            Triple(
                pagerState.currentPage,
                pagerState.settledPage,
                pagerState.targetPage
            )
        }.collect {
            Log.d(
                "PagerState",
                "OnPageChanged: [C]${it.first}, [S]${it.second}, [T]${it.third}"
            )
        }
    }
    return remember(coroutineScope, navController, pagerState) {
        AppState(coroutineScope, navController, pagerState)
    }
}

@Stable
class AppState(
    private val coroutineScope: CoroutineScope,
    val navController: NavHostController,
    val pagerState: PagerState
) {
    val currentComposableNavDestination: NavDestination?
        @Composable get() = navController.currentBackStackEntryAsState().value?.destination

    private val currentNavDestination: NavDestination?
        get() = navController.currentBackStackEntry?.destination

    val currentComposableTopLevelDestination: TopLevelDestination?
        @Composable get() = when (currentComposableNavDestination?.route) {
            rootNavigationRoute -> {
                topLevelDestinations[pagerState.currentPage]
            }

            else -> null
        }

    val topLevelDestinations: List<TopLevelDestination> = TopLevelDestination.values().asList()

    fun navigateToTopLevelDestination(destination: TopLevelDestination) {
        if (currentNavDestination notDestinationTo Destination.Root::class.java) {
            navController.popUpToRoot()
        }
        coroutineScope.launch {
            val index = topLevelDestinations.indexOf(destination)
            pagerState.scrollToPage(index)
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