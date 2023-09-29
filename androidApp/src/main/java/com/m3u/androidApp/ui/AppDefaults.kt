package com.m3u.androidApp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavDestination
import com.m3u.androidApp.navigation.Destination
import com.m3u.androidApp.navigation.destinationTo
import com.m3u.androidApp.navigation.notDestinationTo
import com.m3u.androidApp.navigation.safeDestinationTo
import com.m3u.ui.model.EmptyHelper

object AppDefaults {
    val EmptyHelperConnector: HelperConnector = { _, _, _, _, _, _ -> EmptyHelper }

    @Composable
    fun isSystemBarVisible(currentDestination: NavDestination?): Boolean =
        currentDestination notDestinationTo Destination.Live::class.java &&
                currentDestination notDestinationTo Destination.LivePlayList::class.java

    @Composable
    fun isSystemBarScrollable(currentDestination: NavDestination?): Boolean =
//        currentDestination notDestinationTo Destination.Root::class.java
        false

    @Composable
    fun isBackPressedVisible(currentDestination: NavDestination?): Boolean =
        !currentDestination.safeDestinationTo<Destination.Root>(true)
//        false

    @Composable
    fun isPlaying(currentDestination: NavDestination?): Boolean = remember(currentDestination) {
        currentDestination destinationTo Destination.Live::class.java ||
                currentDestination destinationTo Destination.LivePlayList::class.java
    }
}