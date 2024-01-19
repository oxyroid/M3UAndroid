package com.m3u.androidApp.ui

import android.util.Log
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.m3u.ui.Destination
import com.m3u.ui.Destination.Root.Setting.SettingFragment
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun rememberAppState(
    navController: NavHostController = rememberNavController(),
    pagerState: PagerState = rememberPagerState { Destination.Root.entries.size },
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    onBackPressedDispatcherOwner: OnBackPressedDispatcherOwner? = LocalOnBackPressedDispatcherOwner.current
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
    return remember(navController, pagerState, coroutineScope, onBackPressedDispatcherOwner) {
        AppState(navController, pagerState, coroutineScope, onBackPressedDispatcherOwner)
    }
}

@Stable
class AppState(
    val navController: NavHostController,
    val pagerState: PagerState,
    val coroutineScope: CoroutineScope,
    private val onBackPressedDispatcherOwner: OnBackPressedDispatcherOwner?
) {
    val navDestination: NavDestination?
        @Composable get() = navController.currentBackStackEntryAsState().value?.destination

    val rootDestination: Destination.Root?
        @Composable get() = when (navDestination?.route) {
            ROOT_ROUTE -> rootDestinations[pagerState.currentPage]
            else -> null
        }

    fun navigateToRoot(destination: Destination.Root) {
        navController.popupToRoot()
        coroutineScope.launch {
            val page = when (destination) {
                Destination.Root.Foryou -> 0
                Destination.Root.Favourite -> 1
                is Destination.Root.Setting -> 2
            }
            _rootDestinations = persistentListOf(
                Destination.Root.Foryou,
                Destination.Root.Favourite,
                if (destination is Destination.Root.Setting) destination
                else Destination.Root.Setting(SettingFragment.Root)
            )
            pagerState.scrollToPage(page)
        }
    }

    fun onBackClick() {
        onBackPressedDispatcherOwner?.onBackPressedDispatcher?.onBackPressed()
    }

    private var _rootDestinations: List<Destination.Root> by mutableStateOf(Destination.Root.entries)
    val rootDestinations: ImmutableList<Destination.Root> get() = _rootDestinations.toPersistentList()
}
