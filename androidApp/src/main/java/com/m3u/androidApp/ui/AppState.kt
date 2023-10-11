package com.m3u.androidApp.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.m3u.androidApp.navigation.popupToRoot
import com.m3u.androidApp.navigation.rootRoute
import com.m3u.features.about.navigation.aboutRoute
import com.m3u.features.about.navigation.navigateToAbout
import com.m3u.features.console.navigation.consoleRoute
import com.m3u.features.console.navigation.navigateToConsole
import com.m3u.features.feed.navigation.feedRoute
import com.m3u.features.feed.navigation.navigateToFeed
import com.m3u.features.live.navigation.livePlaylistRoute
import com.m3u.features.live.navigation.liveRoute
import com.m3u.features.live.navigation.navigateToLive
import com.m3u.features.live.navigation.navigateToLivePlayList
import com.m3u.ui.Destination

@Composable
fun rememberAppState(
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
    return remember(navController) {
        AppState(navController)
    }
}

@Stable
class AppState(
    val navController: NavHostController
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

    var currentPage by mutableIntStateOf(0)

    val navDestination: NavDestination?
        @Composable get() = navController.currentBackStackEntryAsState().value?.destination

    val rootDestination: Destination.Root?
        @Composable get() = when (navDestination?.route) {
            rootRoute -> rootDestinations[currentPage]
            else -> null
        }

    fun navigateTo(destination: Destination) {
        when (destination) {
            is Destination.Root -> {
                navController.popupToRoot()
                currentPage = rootDestinations.indexOf(destination)
            }

            is Destination.Feed -> navController.navigateToFeed(destination.url)
            is Destination.Live -> navController.navigateToLive(destination.id)
            is Destination.LivePlayList -> with(destination) {
                navController.navigateToLivePlayList(ids, initial)
            }

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
        Destination.Root::class.java.name -> rootRoute
        Destination.Live::class.java.name -> liveRoute
        Destination.LivePlayList::class.java.name -> livePlaylistRoute
        Destination.Feed::class.java.name -> feedRoute
        Destination.Console::class.java.name -> consoleRoute
        Destination.About::class.java.name -> aboutRoute
        else -> rootRoute
    }

    return route == targetRoute
}