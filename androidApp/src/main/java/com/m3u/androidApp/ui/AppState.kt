package com.m3u.androidApp.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.m3u.androidApp.navigation.Destination
import com.m3u.ui.TopLevelDestination
import com.m3u.androidApp.navigation.notDestinationTo
import com.m3u.androidApp.navigation.popupToRoot
import com.m3u.androidApp.navigation.rootNavigationRoute
import com.m3u.features.about.navigation.navigateToAbout
import com.m3u.features.console.navigation.navigateToConsole
import com.m3u.features.feed.navigation.navigateToFeed
import com.m3u.features.live.navigation.navigateToLive
import com.m3u.features.live.navigation.navigateToLivePlayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

typealias NavigateToDestination = (destination: Destination) -> Unit

@Composable
fun rememberAppState(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    navController: NavHostController = rememberNavController()
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
    return remember(coroutineScope, navController) {
        AppState(coroutineScope, navController)
    }
}

@Stable
class AppState(
    private val coroutineScope: CoroutineScope,
    val navController: NavHostController
) {
    // Initially, we stored the "pagerState" here. However, we encountered an unexpected behavior
    // with "scrollToPage/animateScrollToPage". This function did not update the pagerState#currentPage as intended.
    // This issue arose due to the pager being invisible while navigating through child screens.
    //
    // To address this, we decided to store "currentPage: Int" here. Both the bottom bar and the pager
    // now observe this value. Here's how it works:
    // 1. When a user selects a bottom bar item on the root screen, the currentPage is modified. The pager responds to this change,
    //    performing an animateScrollToPage to page 2, resulting in the desired behavior.
    // 2. When a user scrolls the page using gestures on the root screen, we monitor the "pagerState#currentPage" to make adjustments
    //    to the currentPage. This ensures that the bottom bar functions correctly.
    //    However, this approach triggers pagerState#animateScrollToPage due to the pager's observation, causing unintended effects.
    // 3. When a user selects a bottom bar item on child screens, the function "navigateToTopLevelDestination" is invoked.
    //    Similar to scenario #1, this works effectively.
    //
    // To resolve the issue outlined in #2, we introduced a modification. Before the pager responds to the currentPager value,
    // it now checks against the pagerState#currentPage. This change helps prevent the undesired behavior.
    //
    // It's important to note that this solution might not be the optimal one, and further improvements could be explored.

    var currentPage by mutableIntStateOf(0)
    val currentComposableNavDestination: NavDestination?
        @Composable get() = navController.currentBackStackEntryAsState().value?.destination

    private val currentNavDestination: NavDestination?
        get() = navController.currentBackStackEntry?.destination

    val currentTopLevelDestination: TopLevelDestination?
        @Composable get() = when (currentComposableNavDestination?.route) {
            rootNavigationRoute -> topLevelDestinations[currentPage]
            else -> null
        }

    val topLevelDestinations: List<TopLevelDestination> = TopLevelDestination.values().asList()

    fun navigateToTopLevelDestination(destination: TopLevelDestination) {
        coroutineScope.launch {
            if (currentNavDestination notDestinationTo Destination.Root::class.java) {
                navController.popupToRoot()
            }
            val index = topLevelDestinations.indexOf(destination)
            currentPage = index
        }
    }

    fun navigateToDestination(destination: Destination) {
        when (destination) {
            Destination.Root -> navController.popupToRoot()
            is Destination.Feed -> navController.navigateToFeed(destination.url)
            is Destination.Live -> navController.navigateToLive(destination.id)
            is Destination.LivePlayList -> navController.navigateToLivePlayList(
                destination.ids,
                destination.initial
            )

            Destination.Console -> navController.navigateToConsole()
            Destination.About -> navController.navigateToAbout()
        }
    }

    fun onBackClick() {
        navController.popBackStack()
    }
}