package com.m3u.features.about.navigation

import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.m3u.features.about.AboutRoute

const val ABOUT_ROUTE = "about_route"

fun NavController.navigateToAbout(navOptions: NavOptions? = null) {
    this.navigate(ABOUT_ROUTE, navOptions)
}

fun NavGraphBuilder.aboutScreen(
    contentPadding: PaddingValues
) {
    composable(
        route = ABOUT_ROUTE,
        enterTransition = { slideInVertically { it } },
        exitTransition = { slideOutVertically { it } },
        popEnterTransition = { slideInVertically { it } },
        popExitTransition = { slideOutVertically { it } }
    ) {
        AboutRoute(contentPadding)
    }
}
