package com.m3u.androidApp.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination
import com.m3u.core.util.basic.title
import com.m3u.ui.Destination
import com.m3u.ui.model.ABlackTheme
import com.m3u.ui.model.DayTheme
import com.m3u.ui.model.NightTheme
import com.m3u.ui.model.Theme

object AppDefaults {
    @Composable
    fun isSystemBarVisible(currentDestination: NavDestination?): Boolean {
        currentDestination ?: return true
        return !(currentDestination destinationTo Destination.Live::class.java ||
                currentDestination destinationTo Destination.LivePlayList::class.java)
    }

    @Composable
    fun isSystemBarScrollable(currentDestination: NavDestination?): Boolean =
//        currentDestination notDestinationTo Destination.Root::class.java
        false

    @Composable
    fun isBackPressedVisible(currentDestination: NavDestination?): Boolean {
        currentDestination ?: return false
        return !(currentDestination destinationTo Destination.Root::class.java)
    }

    @Composable
    fun isPlaying(currentDestination: NavDestination?): Boolean = remember(currentDestination) {
        if (currentDestination == null) false
        else {
            currentDestination destinationTo Destination.Live::class.java ||
                    currentDestination destinationTo Destination.LivePlayList::class.java
        }
    }

    @Composable
    fun title(
        rootDestination: Destination.Root?,
        destination: State<String>
    ): State<String> {
        val context = LocalContext.current
        val defaultValue by destination
        return remember(rootDestination) {
            derivedStateOf {
                (rootDestination
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