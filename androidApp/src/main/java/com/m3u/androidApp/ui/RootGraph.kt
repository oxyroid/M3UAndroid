package com.m3u.androidApp.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import com.m3u.ui.LocalVisiblePageInfos

const val ROOT_ROUTE = "root_route"

fun NavController.restoreBackStack() {
    this.popBackStack(ROOT_ROUTE, false)
}

fun NavGraphBuilder.rootGraph(
    currentDestination: () -> Destination.Root,
    contentPadding: PaddingValues,
    navigateToPlaylist: (Playlist) -> Unit,
    navigateToStream: () -> Unit,
    navigateToAbout: () -> Unit,
    navigateToSettingPlaylistManagement: () -> Unit,
) {
    composable(ROOT_ROUTE) {
        RootGraph(
            currentDestination = currentDestination,
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
    currentDestination: () -> Destination.Root,
    contentPadding: PaddingValues,
    navigateToPlaylist: (Playlist) -> Unit,
    navigateToStream: () -> Unit,
    navigateToAbout: () -> Unit,
    navigateToSettingPlaylistManagement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rootDestination = currentDestination()
    val initialPage = remember {
        Destination.Root.entries.indexOf(rootDestination)
    }
    val pagerState = rememberPagerState(initialPage) { Destination.Root.entries.size }
    LaunchedEffect(rootDestination) {
        val page = Destination.Root.entries.indexOf(rootDestination)
        pagerState.scrollToPage(page)
    }
    HorizontalPager(
        state = pagerState,
        userScrollEnabled = false,
        modifier = modifier
            .fillMaxSize()
            .blurEdge(
                edge = Edge.Bottom,
                color = MaterialTheme.colorScheme.background
            )
    ) { page ->
        CompositionLocalProvider(
            LocalVisiblePageInfos provides pagerState.layoutInfo.visiblePagesInfo
        ) {
            when (Destination.Root.entries[page]) {
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
            }
        }
    }
}
