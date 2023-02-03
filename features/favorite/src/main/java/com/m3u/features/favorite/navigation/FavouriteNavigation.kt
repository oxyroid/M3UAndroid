package com.m3u.features.favorite.navigation

import androidx.compose.animation.*
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import com.google.accompanist.navigation.animation.composable
import com.m3u.features.favorite.FavouriteRoute
import com.m3u.ui.model.SetActions

const val favouriteNavigationRoute = "favourite_route"

fun NavController.navigateToFavourite(navOptions: NavOptions? = null) {
    this.navigate(favouriteNavigationRoute, navOptions)
}

@OptIn(ExperimentalAnimationApi::class)
fun NavGraphBuilder.favouriteScreen(
    setAppActions: SetActions,
    navigateToLive: (Int) -> Unit
) {
    composable(
        route = favouriteNavigationRoute
    ) {
        FavouriteRoute(
            setAppActions = setAppActions,
            navigateToLive = navigateToLive
        )
    }
}