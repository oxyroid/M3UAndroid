package com.m3u.androidApp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.m3u.data.database.model.Playlist
import com.m3u.features.favorite.FavouriteRoute
import com.m3u.features.foryou.ForyouRoute
import com.m3u.features.setting.SettingRoute
import com.m3u.material.ktx.Edge
import com.m3u.material.ktx.blurEdge
import com.m3u.ui.Destination

const val ROOT_ROUTE = "root_route"

fun NavController.popBackStackToRoot() {
    this.popBackStack(ROOT_ROUTE, false)
}

fun NavGraphBuilder.rootGraph(
    root: Destination.Root?,
    contentPadding: PaddingValues,
    navigateToPlaylist: (Playlist) -> Unit,
    navigateToStream: () -> Unit,
    navigateToAbout: () -> Unit,
    navigateToSettingPlaylistManagement: () -> Unit,
) {
    composable(ROOT_ROUTE) {
        RootGraph(
            root = root,
            contentPadding = contentPadding,
            navigateToPlaylist = navigateToPlaylist,
            navigateToStream = navigateToStream,
            navigateToAbout = navigateToAbout,
            navigateToSettingPlaylistManagement = navigateToSettingPlaylistManagement
        )
    }
}

@Composable
private fun RootGraph(
    root: Destination.Root?,
    contentPadding: PaddingValues,
    navigateToPlaylist: (Playlist) -> Unit,
    navigateToStream: () -> Unit,
    navigateToAbout: () -> Unit,
    navigateToSettingPlaylistManagement: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .blurEdge(
                edge = Edge.Bottom,
                color = MaterialTheme.colorScheme.background
            )
    ) {
        when (root) {
            Destination.Root.Foryou -> {
                ForyouRoute(
                    navigateToPlaylist = navigateToPlaylist,
                    navigateToStream = navigateToStream,
                    navigateToSettingPlaylistManagement = navigateToSettingPlaylistManagement,
                    contentPadding = contentPadding,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Destination.Root.Favourite -> {
                FavouriteRoute(
                    navigateToStream = navigateToStream,
                    contentPadding = contentPadding,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Destination.Root.Setting -> {
                SettingRoute(
                    navigateToAbout = navigateToAbout,
                    contentPadding = contentPadding,
                    modifier = Modifier.fillMaxSize()
                )
            }
            null -> {}
        }
    }
}
