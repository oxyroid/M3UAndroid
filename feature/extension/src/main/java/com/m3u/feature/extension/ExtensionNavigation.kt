package com.m3u.feature.extension

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable


private const val EXTENSION_ROUTE_PATH = "extension_route"

object ExtensionNavigation {
    const val EXTENSION_ROUTE = EXTENSION_ROUTE_PATH
    internal fun createExtensionRoute(): String = EXTENSION_ROUTE_PATH
}

fun NavController.navigateToExtension(
    navOptions: NavOptions? = null
) {
    val route = ExtensionNavigation.EXTENSION_ROUTE
    this.navigate(route, navOptions)
}

fun NavGraphBuilder.extensionScreen(
    contentPadding: PaddingValues = PaddingValues()
) {
    composable(
        route = ExtensionNavigation.EXTENSION_ROUTE,
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