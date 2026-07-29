package com.m3u.business.playlist.configuration

import androidx.navigation.NavController
import androidx.navigation.NavOptions
import com.m3u.data.worker.playlistWorkTag

private const val PLAYLIST_CONFIGURATION_ROUTE_PATH = "playlist_configuration_route"

object PlaylistConfigurationNavigation {
    const val TYPE_PLAYLIST_URL = "playlist_url"

    const val PLAYLIST_CONFIGURATION_ROUTE =
        "$PLAYLIST_CONFIGURATION_ROUTE_PATH?$TYPE_PLAYLIST_URL={$TYPE_PLAYLIST_URL}"

    internal fun createPlaylistConfigurationRoute(playlistUrl: String): String {
        val playlistReference = playlistConfigurationReference(playlistUrl)
        return "$PLAYLIST_CONFIGURATION_ROUTE_PATH?$TYPE_PLAYLIST_URL=$playlistReference"
    }
}

fun playlistConfigurationReference(playlistUrl: String): String =
    playlistWorkTag(playlistUrl)

internal fun resolvePlaylistConfigurationUrl(
    playlistUrls: Iterable<String>,
    routeArgument: String,
): String? {
    if (routeArgument.isBlank()) return null

    var legacyRawUrlMatch: String? = null
    playlistUrls.forEach { playlistUrl ->
        if (playlistConfigurationReference(playlistUrl) == routeArgument) {
            return playlistUrl
        }
        if (playlistUrl == routeArgument) {
            legacyRawUrlMatch = playlistUrl
        }
    }
    return legacyRawUrlMatch
}

fun NavController.navigateToPlaylistConfiguration(
    playlistUrl: String,
    navOptions: NavOptions? = null,
) {
    val route = PlaylistConfigurationNavigation.createPlaylistConfigurationRoute(playlistUrl)
    this.navigate(route, navOptions)
}
