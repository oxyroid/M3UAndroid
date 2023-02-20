package com.m3u.features.console.navigation

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import com.google.accompanist.navigation.animation.composable
import com.m3u.features.console.ConsoleRoute

const val consoleRoute = "console_route"

fun NavController.navigateToConsole(navOptions: NavOptions? = null) {
    this.navigate(consoleRoute, navOptions)
}

@OptIn(ExperimentalAnimationApi::class)
fun NavGraphBuilder.consoleScreen() {
    composable(
        route = consoleRoute
    ) {
        ConsoleRoute()
    }
}
