package com.m3u.features.about.navigation

import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.m3u.features.about.AboutRoute

const val aboutRoute = "about_route"

fun NavController.navigateToAbout(navOptions: NavOptions? = null) {
    this.navigate(aboutRoute, navOptions)
}

fun NavGraphBuilder.aboutScreen() {
    composable(
        route = aboutRoute,
        enterTransition = { slideInVertically { it } },
        exitTransition = { slideOutVertically { it } },
        popEnterTransition = { slideInVertically { it } },
        popExitTransition = { slideOutVertically { it } }
    ) {
        AboutRoute()
    }
}