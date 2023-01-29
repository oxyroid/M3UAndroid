package com.m3u.ui

import android.util.Log
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.*
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navOptions
import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import com.m3u.favorite.navigation.favouriteNavigationRoute
import com.m3u.favorite.navigation.navigateToFavourite
import com.m3u.features.live.navigation.navigateToLive
import com.m3u.features.main.navgation.mainNavigationRoute
import com.m3u.features.main.navgation.navigateToMain
import com.m3u.features.setting.navigation.navigateToSetting
import com.m3u.features.setting.navigation.settingNavigationRoute
import com.m3u.navigation.Destination
import com.m3u.navigation.TopLevelDestination
import com.m3u.subscription.navigation.navigationToSubscription
import com.m3u.ui.model.AppAction
import kotlinx.coroutines.CoroutineScope

@Composable
fun rememberM3UAppState(
    @OptIn(ExperimentalAnimationApi::class)
    navController: NavHostController = rememberAnimatedNavController(),
    coroutineScope: CoroutineScope = rememberCoroutineScope()
): M3UAppState {
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            Log.d("AppState", "OnDestinationChanged: $destination")
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
    val currentNavDestination: NavDestination?
        @Composable get() = navController
            .currentBackStackEntryAsState().value?.destination

    val currentTopLevelDestination: TopLevelDestination?
        @Composable get() = when (currentNavDestination?.route) {
            mainNavigationRoute -> TopLevelDestination.Main
            settingNavigationRoute -> TopLevelDestination.Setting
            favouriteNavigationRoute -> TopLevelDestination.Favourite
            else -> null
        }

    val isSystemBarVisibility: Boolean
        @Composable get() = currentTopLevelDestination != null

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
            TopLevelDestination.Main -> navController.navigateToMain(topLevelNavOptions)
            TopLevelDestination.Favourite -> navController.navigateToFavourite(topLevelNavOptions)
            TopLevelDestination.Setting -> navController.navigateToSetting(topLevelNavOptions)
        }
    }

    private val _label: MutableState<String?> = mutableStateOf(null)
    val label: State<String?> get() = _label

    fun navigateToDestination(
        destination: Destination,
        label: String? = ""
    ) {
        val navOptions = navOptions {
//            anim {
//                enter = android.R.anim.slide_in_left
//                exit = android.R.anim.slide_out_right
//            }
        }
        when (destination) {
            is Destination.Subscription -> {
                _label.value = label
                navController.navigationToSubscription(
                    destination.url,
                    navOptions
                )
            }

            is Destination.Live -> {
                navController.navigateToLive(
                    destination.id,
                    navOptions
                )
            }
        }
    }


    private val _appActions: MutableState<List<AppAction>> = mutableStateOf(emptyList())
    val appActions: State<List<AppAction>> get() = _appActions

    fun setActions(actions: List<AppAction>) {
        _appActions.value = actions
    }

    fun onBackClick() {
        navController.popBackStack()
    }
}