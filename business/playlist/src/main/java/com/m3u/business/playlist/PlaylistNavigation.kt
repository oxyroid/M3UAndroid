package com.m3u.business.playlist

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavOptions

const val PLAYLIST_ROUTE_PATH = "playlist_route"

object PlaylistNavigation {
    const val TYPE_URL = "url"
    const val TYPE_CATEGORY = "category"

    const val PLAYLIST_ROUTE =
        "$PLAYLIST_ROUTE_PATH/{$TYPE_URL}?$TYPE_CATEGORY={$TYPE_CATEGORY}"

    internal fun createPlaylistRoute(url: String, category: String? = null): String {
        val base = "$PLAYLIST_ROUTE_PATH/$url"
        return if (category != null) "$base?${TYPE_CATEGORY}=${Uri.encode(category)}" else base
    }
}

fun NavController.navigateToPlaylist(
    playlistUrl: String,
    category: String? = null,
    navOptions: NavOptions? = null,
) {
    val encodedUrl = Uri.encode(playlistUrl)
    val route = PlaylistNavigation.createPlaylistRoute(encodedUrl, category)
    this.navigate(route, navOptions)
}
