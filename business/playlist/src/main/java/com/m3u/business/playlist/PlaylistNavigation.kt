package com.m3u.business.playlist

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavOptions

const val PLAYLIST_ROUTE_PATH = "playlist_route"

object PlaylistNavigation {
    const val TYPE_URL = "url"

    const val PLAYLIST_ROUTE =
        "$PLAYLIST_ROUTE_PATH?$TYPE_URL={$TYPE_URL}"

    internal fun createPlaylistRoute(url: String): String {
        return "$PLAYLIST_ROUTE_PATH?$TYPE_URL=$url"
    }
}

fun NavController.navigateToPlaylist(
    playlistUrl: String,
    navOptions: NavOptions? = null,
) {
    val encodedUrl = Uri.encode(playlistUrl)
    val route = PlaylistNavigation.createPlaylistRoute(encodedUrl)
    this.navigate(route, navOptions)
}
