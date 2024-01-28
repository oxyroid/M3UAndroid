package com.m3u.androidApp.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.m3u.core.wrapper.eventOf
import com.m3u.core.wrapper.handledEvent
import com.m3u.data.database.model.Playlist
import com.m3u.features.favorite.FavouriteRoute
import com.m3u.features.foryou.ForyouRoute
import com.m3u.features.setting.SettingRoute
import com.m3u.material.ktx.Edge
import com.m3u.material.ktx.blurEdge
import com.m3u.ui.Destination
import com.m3u.ui.ResumeEvent

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

    val rootDestinations = remember { Destination.Root.entries }
    val pagerState = rememberPagerState(
        pageCount = { rootDestinations.size }
    )

    LaunchedEffect(root, rootDestinations) {
        val page = rootDestinations.indexOf(root).coerceAtLeast(0)
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
    ) { pagerIndex ->
        when (rootDestinations[pagerIndex]) {
            Destination.Root.Foryou -> {
                ForyouRoute(
                    navigateToPlaylist = navigateToPlaylist,
                    navigateToStream = navigateToStream,
                    navigateToSettingPlaylistManagement = navigateToSettingPlaylistManagement,
                    resume = rememberResumeEvent(pagerState.settledPage, pagerIndex),
                    contentPadding = contentPadding,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Destination.Root.Favourite -> {
                FavouriteRoute(
                    navigateToStream = navigateToStream,
                    resume = rememberResumeEvent(pagerState.settledPage, pagerIndex),
                    contentPadding = contentPadding,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Destination.Root.Setting -> {
                SettingRoute(
                    navigateToAbout = navigateToAbout,
                    contentPadding = contentPadding,
                    resume = rememberResumeEvent(pagerState.settledPage, pagerIndex),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun rememberResumeEvent(currentPage: Int, targetPage: Int): ResumeEvent =
    remember(currentPage, targetPage) {
        if (currentPage == targetPage) eventOf(Unit)
        else handledEvent()
    }
