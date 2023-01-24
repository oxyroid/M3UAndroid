package com.m3u.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Videocam
import com.m3u.R
import com.m3u.core.icon.Icon

enum class TopLevelDestination(
    val selectedIcon: Icon,
    val unselectedIcon: Icon,
    @StringRes val iconTextId: Int,
    @StringRes val titleTextId: Int
) {
    MAIN(
        selectedIcon = Icon.ImageVectorIcon(Icons.Rounded.Home),
        unselectedIcon = Icon.ImageVectorIcon(Icons.Outlined.Home),
        iconTextId = R.string.destination_main,
        titleTextId = R.string.app_name
    ),
    LIVE(
        selectedIcon = Icon.ImageVectorIcon(Icons.Rounded.Videocam),
        unselectedIcon = Icon.ImageVectorIcon(Icons.Outlined.Videocam),
        iconTextId = R.string.destination_live,
        titleTextId = R.string.title_live
    ),
    SETTING(
        selectedIcon = Icon.ImageVectorIcon(Icons.Rounded.Settings),
        unselectedIcon = Icon.ImageVectorIcon(Icons.Outlined.Settings),
        iconTextId = R.string.destination_setting,
        titleTextId = R.string.title_setting
    )
}