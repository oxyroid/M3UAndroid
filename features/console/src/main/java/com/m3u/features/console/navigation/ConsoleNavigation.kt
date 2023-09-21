package com.m3u.features.console.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.m3u.features.console.ConsoleRoute

const val consoleRoute = "console_route"

fun NavController.navigateToConsole(navOptions: NavOptions? = null) {
    this.navigate(consoleRoute, navOptions)
}

fun NavGraphBuilder.consoleScreen() {
    composable(
        route = consoleRoute
    ) {
        ConsoleRoute()
    }
}
