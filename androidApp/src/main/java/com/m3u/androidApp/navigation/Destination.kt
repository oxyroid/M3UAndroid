package com.m3u.androidApp.navigation

import androidx.navigation.NavDestination
import com.m3u.features.console.navigation.consoleRoute
import com.m3u.features.feed.navigation.feedRoute
import com.m3u.features.live.navigation.livePlaylistRoute
import com.m3u.features.live.navigation.liveRoute

sealed interface Destination {
    data object Root : Destination

    data class Feed(
        val url: String,
    ) : Destination

    data class Live(
        val id: Int,
    ) : Destination

    data class LivePlayList(
        val ids: List<Int>,
        val initial: Int
    ) : Destination

    data object Console : Destination
}

inline infix fun <reified D : Destination> NavDestination?.destinationTo(clazz: Class<D>): Boolean {
    val targetRoute = when (clazz.name) {
        Destination.Live::class.java.name -> liveRoute
        Destination.LivePlayList::class.java.name -> livePlaylistRoute
        Destination.Feed::class.java.name -> feedRoute
        Destination.Console::class.java.name -> consoleRoute
        Destination.Root::class.java.name -> rootNavigationRoute
        else -> return false
    }
    return this?.route == targetRoute
}

inline infix fun <reified D : Destination> NavDestination?.notDestinationTo(clazz: Class<D>): Boolean {
    return (this destinationTo clazz).not()
}

inline infix fun <reified D : Destination> NavDestination?.safeDestinationTo(
    includeNullValue: Boolean
): Boolean {
    this ?: return includeNullValue
    return this destinationTo D::class.java
}