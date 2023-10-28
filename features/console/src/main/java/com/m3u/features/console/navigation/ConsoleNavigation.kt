package com.m3u.features.console.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.m3u.features.console.ConsoleRoute

const val CONSOLE_ROUTE = "console_route"

fun NavController.navigateToConsole(navOptions: NavOptions? = null) {
    this.navigate(CONSOLE_ROUTE, navOptions)
}

fun NavGraphBuilder.consoleScreen() {
    composable(
        route = CONSOLE_ROUTE,
        enterTransition = { slideInVertically { it } },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutVertically { it } }
    ) {
        ConsoleRoute()
    }
}
