package com.m3u.smartphone.ui.business.configuration

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.m3u.business.playlist.configuration.PlaylistConfigurationNavigation

fun NavGraphBuilder.playlistConfigurationScreen(
    contentPadding: PaddingValues = PaddingValues(),
) {
    composable(
        route = PlaylistConfigurationNavigation.PLAYLIST_CONFIGURATION_ROUTE,
        arguments = listOf(
            navArgument(PlaylistConfigurationNavigation.TYPE_PLAYLIST_URL) {
                type = NavType.StringType
            }
        ),
        enterTransition = { slideInVertically { it } },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutVertically { it } }
    ) {
        PlaylistConfigurationRoute(
            contentPadding = contentPadding
        )
    }
}
