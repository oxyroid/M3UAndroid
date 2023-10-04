package com.m3u.androidApp.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import com.m3u.androidApp.navigation.Destination
import com.m3u.androidApp.navigation.destinationTo
import com.m3u.androidApp.navigation.notDestinationTo
import com.m3u.androidApp.navigation.safeDestinationTo
import com.m3u.core.util.basic.title
import com.m3u.ui.TopLevelDestination
import com.m3u.ui.model.ABlackTheme
import com.m3u.ui.model.DayTheme
import com.m3u.ui.model.EmptyHelper
import com.m3u.ui.model.NightTheme
import com.m3u.ui.model.Theme
import kotlinx.coroutines.flow.StateFlow

object AppDefaults {
    val EmptyHelperConnector: HelperConnector = { _, _, _ -> EmptyHelper }

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

    @Composable
    fun title(
        destination: TopLevelDestination?,
        default: StateFlow<String>
    ): State<String> {
        val context = LocalContext.current
        val defaultValue by default.collectAsStateWithLifecycle()
        return remember(destination) {
            derivedStateOf {
                (destination
                    ?.titleTextId
                    ?.let(context::getString)
                    ?: defaultValue)
                    .title()
            }
        }
    }

    @Composable
    fun theme(cinemaMode: Boolean): Theme = when {
        cinemaMode -> ABlackTheme
        isSystemInDarkTheme() -> NightTheme
        else -> DayTheme
    }
}