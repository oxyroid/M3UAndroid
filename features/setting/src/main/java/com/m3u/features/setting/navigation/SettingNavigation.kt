package com.m3u.features.setting.navigation

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import com.google.accompanist.navigation.animation.composable
import com.m3u.features.setting.SettingRoute

const val settingNavigationRoute = "setting_route"

fun NavController.navigateToSetting(navOptions: NavOptions? = null) {
    this.navigate(settingNavigationRoute, navOptions)
}

@OptIn(ExperimentalAnimationApi::class)
fun NavGraphBuilder.settingScreen() {
    composable(settingNavigationRoute) {
        SettingRoute()
    }
}
