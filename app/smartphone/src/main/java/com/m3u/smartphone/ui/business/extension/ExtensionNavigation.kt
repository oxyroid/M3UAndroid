package com.m3u.smartphone.ui.business.extension

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable

fun NavGraphBuilder.extensionScreen(
    contentPadding: PaddingValues = PaddingValues(),
) {
    composable(
        route = "extension_route",
        enterTransition = { slideInVertically { it } },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutVertically { it } }
    ) {
        ExtensionRoute(
            contentPadding = contentPadding
        )
    }
}

fun NavController.navigateToExtension(
    navOptions: NavOptions? = null,
) {
    this.navigate("extension_route", navOptions)
}
