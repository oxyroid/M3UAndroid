package com.m3u.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import com.m3u.app.R
import com.m3u.ui.model.Icon

enum class TopLevelDestination(
    val selectedIcon: Icon,
    val unselectedIcon: Icon,
    @StringRes val iconTextId: Int,
    @StringRes val titleTextId: Int
) {
    Main(
        selectedIcon = Icon.ImageVectorIcon(Icons.Rounded.Home),
        unselectedIcon = Icon.ImageVectorIcon(Icons.Outlined.Home),
        iconTextId = R.string.destination_main,
        titleTextId = R.string.app_name
    ),
    Favourite(
        selectedIcon = Icon.ImageVectorIcon(Icons.Rounded.Collections),
        unselectedIcon = Icon.ImageVectorIcon(Icons.Outlined.Collections),
        iconTextId = R.string.destination_favourite,
        titleTextId = R.string.title_favourite
    ),
    Setting(
        selectedIcon = Icon.ImageVectorIcon(Icons.Rounded.Settings),
        unselectedIcon = Icon.ImageVectorIcon(Icons.Outlined.Settings),
        iconTextId = R.string.destination_setting,
        titleTextId = R.string.title_setting
    )
}

typealias NavigateToTopLevelDestination = (TopLevelDestination) -> Unit