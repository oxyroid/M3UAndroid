package com.m3u.androidApp.ui

import android.util.Log
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
import androidx.navigation.compose.rememberNavController
import com.m3u.androidApp.navigation.ROOT_ROUTE
import com.m3u.androidApp.navigation.popupToRoot
import com.m3u.features.about.navigation.ABOUT_ROUTE
import com.m3u.features.about.navigation.navigateToAbout
import com.m3u.features.console.navigation.CONSOLE_ROUTE
import com.m3u.features.console.navigation.navigateToConsole
import com.m3u.features.playlist.navigation.PLAYLIST_ROUTE
import com.m3u.features.playlist.navigation.navigateToPlaylist
import com.m3u.ui.Destination
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun rememberAppState(
    navController: NavHostController = rememberNavController(),
    pagerState: PagerState = rememberPagerState { Destination.Root.entries.size },
    coroutineScope: CoroutineScope = rememberCoroutineScope()
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
    return remember(navController, pagerState, coroutineScope) {
        AppState(navController, pagerState, coroutineScope)
    }
}

@Stable
class AppState(
    val navController: NavHostController,
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope
) {
    // Current Root Destination Page.
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

    val navDestination: NavDestination?
        @Composable get() = navController.currentBackStackEntryAsState().value?.destination

    val rootDestination: Destination.Root?
        @Composable get() = when (navDestination?.route) {
            ROOT_ROUTE -> rootDestinations[pagerState.currentPage]
            else -> null
        }

    fun navigate(destination: Destination) {
        when (destination) {
            is Destination.Root -> {
                navController.popupToRoot()
                coroutineScope.launch {
                    val page = rootDestinations.indexOf(destination)
                    pagerState.scrollToPage(page)
                }
            }

            is Destination.Playlist -> navController.navigateToPlaylist(destination.url)
            is Destination.Stream -> {}
            Destination.Console -> navController.navigateToConsole()
            Destination.About -> navController.navigateToAbout()
        }
    }

    fun onBackClick() {
        navController.popBackStack()
    }

    private val rootDestinations: List<Destination.Root> = Destination.Root.entries
}

inline infix fun <reified D : Destination> NavDestination.destinationTo(clazz: Class<D>): Boolean {
    val targetRoute = when (clazz.name) {
        Destination.Root::class.java.name -> ROOT_ROUTE
        Destination.Playlist::class.java.name -> PLAYLIST_ROUTE
        Destination.Console::class.java.name -> CONSOLE_ROUTE
        Destination.About::class.java.name -> ABOUT_ROUTE
        else -> ROOT_ROUTE
    }
    return route == targetRoute
}