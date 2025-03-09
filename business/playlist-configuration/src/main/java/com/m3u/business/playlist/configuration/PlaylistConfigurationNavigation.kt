package com.m3u.business.playlist.configuration

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

private const val PLAYLIST_CONFIGURATION_ROUTE_PATH = "playlist_configuration_route"

object PlaylistConfigurationNavigation {
    const val TYPE_PLAYLIST_URL = "playlist_url"

    const val PLAYLIST_CONFIGURATION_ROUTE =
        "$PLAYLIST_CONFIGURATION_ROUTE_PATH?$TYPE_PLAYLIST_URL={$TYPE_PLAYLIST_URL}"

    internal fun createPlaylistConfigurationRoute(playlistUrl: String): String {
        return "$PLAYLIST_CONFIGURATION_ROUTE_PATH?$TYPE_PLAYLIST_URL=$playlistUrl"
    }
}

fun NavController.navigateToPlaylistConfiguration(
    playlistUrl: String,
    navOptions: NavOptions? = null,
) {
    val encodedUrl = Uri.encode(playlistUrl)
    val route = PlaylistConfigurationNavigation.createPlaylistConfigurationRoute(encodedUrl)
    this.navigate(route, navOptions)
}
