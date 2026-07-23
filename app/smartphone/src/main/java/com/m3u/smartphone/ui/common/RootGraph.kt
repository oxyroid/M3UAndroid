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
import com.m3u.smartphone.ui.business.favourite.FavoriteRoute
import com.m3u.smartphone.ui.business.foryou.ForyouRoute
import com.m3u.smartphone.ui.business.setting.SettingRoute
import com.m3u.smartphone.ui.material.components.Destination
import com.m3u.smartphone.ui.material.ktx.Edge
import com.m3u.smartphone.ui.material.ktx.blurEdge

fun NavGraphBuilder.rootGraph(
    contentPadding: PaddingValues,
    navigateToPlaylist: (Playlist) -> Unit,
    navigateToChannel: () -> Unit,
    navigateToSettingPlaylistManagement: () -> Unit,
    navigateToPlaylistConfiguration: (Playlist) -> Unit,
    showBottomEdgeBlur: Boolean,
    onNestedDetailVisibilityChanged: (Boolean) -> Unit,
) {
    composable(
        route = Destination.Foryou.name,
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
                .then(
                    if (showBottomEdgeBlur) {
                        Modifier.blurEdge(
                            edge = Edge.Bottom,
                            color = MaterialTheme.colorScheme.background,
                        )
                    } else {
                        Modifier
                    },
                ),
        )
    }
    composable(
        route = Destination.Favorite.name,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() }
    ) {
        FavoriteRoute(
            navigateToChannel = navigateToChannel,
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (showBottomEdgeBlur) {
                        Modifier.blurEdge(
                            edge = Edge.Bottom,
                            color = MaterialTheme.colorScheme.background,
                        )
                    } else {
                        Modifier
                    },
                ),
        )
    }

    composable(
        route = Destination.Setting.name,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() }
    ) {
        SettingRoute(
            contentPadding = contentPadding,
            onDetailVisibilityChanged = onNestedDetailVisibilityChanged,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (showBottomEdgeBlur) {
                        Modifier.blurEdge(
                            edge = Edge.Bottom,
                            color = MaterialTheme.colorScheme.background,
                        )
                    } else {
                        Modifier
                    },
                ),
        )
    }
}
