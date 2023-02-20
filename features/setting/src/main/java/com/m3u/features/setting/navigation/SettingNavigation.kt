package com.m3u.features.setting.navigation

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import com.google.accompanist.navigation.animation.composable
import com.m3u.features.setting.SettingRoute

typealias NavigateToConsole = () -> Unit

const val settingRoute = "setting_route"

fun NavController.navigateToSetting(navOptions: NavOptions? = null) {
    this.navigate(settingRoute, navOptions)
}

@OptIn(ExperimentalAnimationApi::class)
fun NavGraphBuilder.settingScreen(
    navigateToConsole: NavigateToConsole
) {
    composable(
        route = settingRoute
    ) {
        SettingRoute(
            navigateToConsole = navigateToConsole
        )
    }
}
