package com.m3u.favorite.navigation

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import com.google.accompanist.navigation.animation.composable
import com.m3u.favorite.FavouriteRoute

const val favouriteNavigationRoute = "favourite_route"

fun NavController.navigateToFavourite(navOptions: NavOptions? = null) {
    this.navigate(favouriteNavigationRoute, navOptions)
}

@OptIn(ExperimentalAnimationApi::class)
fun NavGraphBuilder.favouriteScreen() {
    composable(favouriteNavigationRoute) {
        FavouriteRoute()
    }
}