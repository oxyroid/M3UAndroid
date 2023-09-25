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

typealias NavigateToTopLevelDestination = (TopLevelDestination) -> Unit

enum class TopLevelDestination(
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    @StringRes val iconTextId: Int,
    @StringRes val titleTextId: Int
) {
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
    )
}