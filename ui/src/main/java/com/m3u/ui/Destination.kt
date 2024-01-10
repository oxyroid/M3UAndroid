package com.m3u.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.m3u.i18n.R.string
import kotlinx.collections.immutable.persistentListOf

@Immutable
sealed interface Destination {
    @Immutable
    sealed class Root(
        val selectedIcon: ImageVector,
        val unselectedIcon: ImageVector,
        @StringRes val iconTextId: Int
    ) : Destination {
        @Immutable
        data object Foryou : Root(
            selectedIcon = Icons.Rounded.Home,
            unselectedIcon = Icons.Outlined.Home,
            iconTextId = string.ui_destination_foryou
        )

        @Immutable
        data object Favourite : Root(
            selectedIcon = Icons.Rounded.Collections,
            unselectedIcon = Icons.Outlined.Collections,
            iconTextId = string.ui_destination_favourite
        )

        @Immutable
        data class Setting(
            val targetFragment: SettingFragment = SettingFragment.Root
        ) : Root(
            selectedIcon = Icons.Rounded.Settings,
            unselectedIcon = Icons.Outlined.Settings,
            iconTextId = string.ui_destination_setting
        ) {
            @Immutable
            enum class SettingFragment {
                Root, Playlists, Scripts, Appearance
            }
        }

        companion object {
            val entries = persistentListOf(Foryou, Favourite, Setting())
        }
    }

    @Immutable
    data class Playlist(
        val url: String,
        val recommend: String? = null
    ) : Destination

    @Immutable
    data object Console : Destination

    @Immutable
    data object About : Destination
}

