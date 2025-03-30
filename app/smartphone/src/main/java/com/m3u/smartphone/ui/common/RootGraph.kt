package com.m3u.smartphone.ui.common

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.m3u.data.database.model.Playlist
import com.m3u.smartphone.ui.material.ktx.Edge
import com.m3u.smartphone.ui.material.ktx.blurEdge
import com.m3u.smartphone.ui.business.favourite.FavoriteRoute
import com.m3u.smartphone.ui.business.foryou.ForyouRoute
import com.m3u.smartphone.ui.business.setting.SettingRoute
import com.m3u.smartphone.ui.material.components.Destination

fun NavGraphBuilder.rootGraph(
    contentPadding: PaddingValues,
    navigateToPlaylist: (Playlist) -> Unit,
    navigateToChannel: () -> Unit,
    navigateToSettingPlaylistManagement: () -> Unit,
    navigateToPlaylistConfiguration: (Playlist) -> Unit,
) {
    composable(
        route = Destination.Root.Foryou.name,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() }
    ) {
        ForyouRoute(
            navigateToPlaylist = navigateToPlaylist,
            navigateToChannel = navigateToChannel,
            navigateToSettingPlaylistManagement = navigateToSettingPlaylistManagement,
            navigateToPlaylistConfiguration = navigateToPlaylistConfiguration,
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxSize()
                .blurEdge(
                    edge = Edge.Bottom,
                    color = MaterialTheme.colorScheme.background
                )
        )
    }
    composable(
        route = Destination.Root.Favorite.name,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() }
    ) {
        FavoriteRoute(
            navigateToChannel = navigateToChannel,
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxSize()
                .blurEdge(
                    edge = Edge.Bottom,
                    color = MaterialTheme.colorScheme.background
                )
        )
    }

    composable(
        route = Destination.Root.Setting.name,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() }
    ) {
        SettingRoute(
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxSize()
                .blurEdge(
                    edge = Edge.Bottom,
                    color = MaterialTheme.colorScheme.background
                )
        )
    }
}
