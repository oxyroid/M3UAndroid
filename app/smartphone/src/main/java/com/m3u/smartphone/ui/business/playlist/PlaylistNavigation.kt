package com.m3u.smartphone.ui.business.playlist

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.m3u.business.playlist.PlaylistNavigation

fun NavGraphBuilder.playlistScreen(
    navigateToChannel: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    composable(
        route = PlaylistNavigation.PLAYLIST_ROUTE,
        arguments = listOf(
            navArgument(PlaylistNavigation.TYPE_URL) {
                type = NavType.StringType
            }
        ),
        enterTransition = { slideInVertically { it } },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutVertically { it } }
    ) {
        PlaylistRoute(
            navigateToChannel = navigateToChannel,
            contentPadding = contentPadding
        )
    }
}
