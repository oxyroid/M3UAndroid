package com.m3u.androidApp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.m3u.data.database.model.Playlist
import com.m3u.feature.favorite.FavouriteRoute
import com.m3u.feature.foryou.ForyouRoute
import com.m3u.feature.setting.SettingRoute
import com.m3u.material.ktx.Edge
import com.m3u.material.ktx.blurEdge
import com.m3u.ui.Destination
import com.m3u.ui.LocalRootDestination

const val ROOT_ROUTE = "root_route"

fun NavController.restoreBackStack() {
    this.popBackStack(ROOT_ROUTE, false)
}

fun NavGraphBuilder.rootGraph(
    contentPadding: PaddingValues,
    navigateToPlaylist: (Playlist) -> Unit,
    navigateToChannel: () -> Unit,
    navigateToSettingPlaylistManagement: () -> Unit,
    navigateToPlaylistConfiguration: (Playlist) -> Unit,
) {
    composable(ROOT_ROUTE) {
        RootGraph(
            contentPadding = contentPadding,
            navigateToPlaylist = navigateToPlaylist,
            navigateToChannel = navigateToChannel,
            navigateToSettingPlaylistManagement = navigateToSettingPlaylistManagement,
            navigateToPlaylistConfiguration = navigateToPlaylistConfiguration
        )
    }
}

@Composable
private fun RootGraph(
    contentPadding: PaddingValues,
    navigateToPlaylist: (Playlist) -> Unit,
    navigateToChannel: () -> Unit,
    navigateToSettingPlaylistManagement: () -> Unit,
    navigateToPlaylistConfiguration: (Playlist) -> Unit,
    modifier: Modifier = Modifier
) {
    val rootDestination = LocalRootDestination.current
    val page by remember(rootDestination) {
        derivedStateOf { Destination.Root.entries.indexOf(rootDestination) }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .blurEdge(
                edge = Edge.Bottom,
                color = MaterialTheme.colorScheme.background
            )
    ) {
        when (Destination.Root.entries[page]) {
            Destination.Root.Foryou -> {
                ForyouRoute(
                    navigateToPlaylist = navigateToPlaylist,
                    navigateToChannel = navigateToChannel,
                    navigateToSettingPlaylistManagement = navigateToSettingPlaylistManagement,
                    navigateToPlaylistConfiguration = navigateToPlaylistConfiguration,
                    contentPadding = contentPadding,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Destination.Root.Favourite -> {
                FavouriteRoute(
                    navigateToChannel = navigateToChannel,
                    contentPadding = contentPadding,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Destination.Root.Setting -> {
                SettingRoute(
                    contentPadding = contentPadding,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
