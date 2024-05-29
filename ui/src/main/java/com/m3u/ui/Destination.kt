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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import com.m3u.i18n.R.string
import kotlinx.parcelize.Parcelize

val LocalNavController =
    staticCompositionLocalOf<NavHostController> { error("Please provide NavHostController") }
val LocalRootDestination = compositionLocalOf<Destination.Root?> { null }

@Immutable
sealed interface Destination {
    @Immutable
    enum class Root(
        val selectedIcon: ImageVector,
        val unselectedIcon: ImageVector,
        @StringRes val iconTextId: Int
    ) : Destination {
        Foryou(
            selectedIcon = Icons.Rounded.Home,
            unselectedIcon = Icons.Outlined.Home,
            iconTextId = string.ui_destination_foryou
        ),
        Favourite(
            selectedIcon = Icons.Rounded.Collections,
            unselectedIcon = Icons.Outlined.Collections,
            iconTextId = string.ui_destination_favourite
        ),
        Setting(
            selectedIcon = Icons.Rounded.Settings,
            unselectedIcon = Icons.Outlined.Settings,
            iconTextId = string.ui_destination_setting
        )
    }

    @Immutable
    data class Playlist(
        val url: String,
        val recommend: String? = null
    ) : Destination
}

@Immutable
@Parcelize
sealed interface SettingDestination : Parcelable {
    @Immutable
    @Parcelize
    data object Default : SettingDestination

    @Immutable
    @Parcelize
    data object Playlists : SettingDestination

    @Immutable
    @Parcelize
    data object Appearance : SettingDestination
}
