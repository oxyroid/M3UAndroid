package com.m3u.ui

import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import com.m3u.i18n.R.string
import kotlinx.parcelize.Parcelize

typealias Navigate = (Destination) -> Unit

@Immutable
data class RootDestinationHolder(
    val roots: List<Destination.Root>
)

@Composable
fun rememberRootDestinationHolder(roots: List<Destination.Root>): RootDestinationHolder {
    return remember(roots) {
        RootDestinationHolder(roots)
    }
}

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
            iconTextId = string.ui_destination_main,
            titleTextId = string.ui_app_name
        ),
        Favourite(
            selectedIcon = Icons.Rounded.Collections,
            unselectedIcon = Icons.Outlined.Collections,
            iconTextId = string.ui_destination_favourite,
            titleTextId = string.ui_title_favourite
        ),
        Setting(
            selectedIcon = Icons.Rounded.Settings,
            unselectedIcon = Icons.Outlined.Settings,
            iconTextId = string.ui_destination_setting,
            titleTextId = string.ui_title_setting
        );
    }

    data class Feed(val url: String) : Destination

    @Parcelize
    data class Live(val id: Int) : Destination, Parcelable

    @Parcelize
    data class LivePlayList(
        val ids: List<Int>,
        val initial: Int
    ) : Destination, Parcelable

    data object Console : Destination

    data object About : Destination
}

