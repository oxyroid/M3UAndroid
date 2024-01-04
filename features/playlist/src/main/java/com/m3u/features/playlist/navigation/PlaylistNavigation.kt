package com.m3u.features.playlist.navigation

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
import com.m3u.features.playlist.PlaylistRoute

private const val PLAYLIST_ROUTE_PATH = "playlist_route"

private const val TYPE_URL = "url"
private const val TYPE_RECOMMEND = "recommend"

const val PLAYLIST_ROUTE = "$PLAYLIST_ROUTE_PATH?$TYPE_URL={$TYPE_URL}&$TYPE_RECOMMEND={$TYPE_RECOMMEND}"
private fun createPlaylistRoute(url: String, recommend: String? = null): String {
    return "$PLAYLIST_ROUTE_PATH?${TYPE_URL}=$url&$TYPE_RECOMMEND=$recommend"
}

fun NavController.navigateToPlaylist(
    playlistUrl: String,
    recommend: String? = null,
    navOptions: NavOptions? = null
) {
    val encodedUrl = Uri.encode(playlistUrl)
    val route = createPlaylistRoute(encodedUrl, recommend)
    this.navigate(route, navOptions)
}

fun NavGraphBuilder.playlistScreen(
    navigateToStream: () -> Unit,
    contentPadding: PaddingValues,
) {
    composable(
        route = PLAYLIST_ROUTE,
        arguments = listOf(
            navArgument(TYPE_URL) {
                type = NavType.StringType
            },
            navArgument(TYPE_RECOMMEND) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        ),
        enterTransition = { slideInVertically { it } },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutVertically { it } }
    ) { navBackStackEntry ->
        val playlistUrl = navBackStackEntry
            .arguments
            ?.getString(TYPE_URL)
            ?.let(Uri::decode)
            .orEmpty()
        val title = navBackStackEntry
            .arguments
            ?.getString(TYPE_RECOMMEND)

        PlaylistRoute(
            playlistUrl = playlistUrl,
            recommend = title,
            navigateToStream = navigateToStream,
            contentPadding = contentPadding
        )
    }
}
