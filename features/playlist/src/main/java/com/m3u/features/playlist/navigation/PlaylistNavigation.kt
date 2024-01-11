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
import androidx.navigation.activity
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.m3u.features.playlist.PlaylistRoute
import com.m3u.features.playlist.TvPlaylistActivity

private const val PLAYLIST_ROUTE_PATH = "playlist_route"
private const val PLAYLIST_TV_ROUTE_PATH = "playlist_tv_route"

object PlaylistNavigation {
    internal const val TYPE_URL = "url"
    internal const val TYPE_RECOMMEND = "recommend"

    const val PLAYLIST_ROUTE =
        "$PLAYLIST_ROUTE_PATH?$TYPE_URL={$TYPE_URL}&$TYPE_RECOMMEND={$TYPE_RECOMMEND}"

    internal fun createPlaylistRoute(url: String, recommend: String? = null): String {
        return "$PLAYLIST_ROUTE_PATH?${TYPE_URL}=$url&$TYPE_RECOMMEND=$recommend"
    }

    internal const val PLAYLIST_TV_ROUTE =
        "$PLAYLIST_TV_ROUTE_PATH?$TYPE_URL={$TYPE_URL}&$TYPE_RECOMMEND={$TYPE_RECOMMEND}"

    internal fun createPlaylistTvRoute(url: String, recommend: String? = null): String {
        return "$PLAYLIST_TV_ROUTE_PATH?${TYPE_URL}=$url&$TYPE_RECOMMEND=$recommend"
    }
}

fun NavController.navigateToPlaylist(
    playlistUrl: String,
    recommend: String? = null,
    tv: Boolean = false,
    navOptions: NavOptions? = null,
) {
    val encodedUrl = Uri.encode(playlistUrl)
    val route = if (tv) PlaylistNavigation.createPlaylistTvRoute(encodedUrl, recommend)
    else PlaylistNavigation.createPlaylistRoute(encodedUrl, recommend)
    this.navigate(route, navOptions)
}

fun NavGraphBuilder.playlistScreen(
    navigateToStream: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    composable(
        route = PlaylistNavigation.PLAYLIST_ROUTE,
        arguments = listOf(
            navArgument(PlaylistNavigation.TYPE_URL) {
                type = NavType.StringType
            },
            navArgument(PlaylistNavigation.TYPE_RECOMMEND) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        ),
        enterTransition = { slideInVertically { it } },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutVertically { it } }
    ) {
        PlaylistRoute(
            navigateToStream = navigateToStream,
            contentPadding = contentPadding
        )
    }
}

fun NavGraphBuilder.playlistTvScreen() {
    activity(PlaylistNavigation.PLAYLIST_TV_ROUTE) {
        activityClass = TvPlaylistActivity::class
        argument(PlaylistNavigation.TYPE_URL) {
            type = NavType.StringType
        }
        argument(PlaylistNavigation.TYPE_RECOMMEND) {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        }
    }
}
