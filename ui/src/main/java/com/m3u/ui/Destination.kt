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
import com.m3u.i18n.R.string
import kotlinx.collections.immutable.persistentListOf

sealed interface Destination {
    sealed class Root(
        val selectedIcon: ImageVector,
        val unselectedIcon: ImageVector,
        @StringRes val iconTextId: Int,
        @StringRes val titleTextId: Int
    ) : Destination {
        data object Foryou : Root(
            selectedIcon = Icons.Rounded.Home,
            unselectedIcon = Icons.Outlined.Home,
            iconTextId = string.ui_destination_foryou,
            titleTextId = string.ui_app_name
        )

        data object Favourite : Root(
            selectedIcon = Icons.Rounded.Collections,
            unselectedIcon = Icons.Outlined.Collections,
            iconTextId = string.ui_destination_favourite,
            titleTextId = string.ui_title_favourite
        )

        data object Setting : Root(
            selectedIcon = Icons.Rounded.Settings,
            unselectedIcon = Icons.Outlined.Settings,
            iconTextId = string.ui_destination_setting,
            titleTextId = string.ui_title_setting
        )

        companion object {
            val entries = persistentListOf(Foryou, Favourite, Setting)
        }
    }

    data class Playlist(
        val url: String,
        val recommend: String? = null
    ) : Destination

    data object Console : Destination

    data object About : Destination
}

