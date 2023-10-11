package com.m3u.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

typealias Navigate = (Destination) -> Unit

sealed interface Destination {
    enum class Root(
        val selectedIcon: ImageVector,
        val unselectedIcon: ImageVector,
        @StringRes val iconTextId: Int,
        @StringRes val titleTextId: Int
    ) : Destination {
        Main(
            selectedIcon = Icons.Rounded.Home,
            unselectedIcon = Icons.Outlined.Home,
            iconTextId = R.string.destination_main,
            titleTextId = R.string.app_name
        ),
        Favourite(
            selectedIcon = Icons.Rounded.Collections,
            unselectedIcon = Icons.Outlined.Collections,
            iconTextId = R.string.destination_favourite,
            titleTextId = R.string.title_favourite
        ),
        Setting(
            selectedIcon = Icons.Rounded.Settings,
            unselectedIcon = Icons.Outlined.Settings,
            iconTextId = R.string.destination_setting,
            titleTextId = R.string.title_setting
        );

        companion object : Key<Root>
    }

    data class Feed(val url: String) : AbstractDestinationElement(Feed) {
        companion object : Key<Feed>
    }

    data class Live(val id: Int) : AbstractDestinationElement(Live) {
        companion object : Key<Live>
    }

    data class LivePlayList(
        val ids: List<Int>,
        val initial: Int
    ) : AbstractDestinationElement(LivePlayList) {
        companion object : Key<LivePlayList>
    }

    data object Console : AbstractDestinationElement(Console), Key<Console>

    data object About : AbstractDestinationElement(About), Key<About>

    interface Key<D : Destination>

    sealed interface Element : Destination {
        val key: Key<*>
    }
}

sealed class AbstractDestinationElement(
    override val key: Destination.Key<*>
) : Destination.Element
