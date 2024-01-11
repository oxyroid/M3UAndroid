package com.m3u.androidApp.ui

import android.util.Log
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
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
import com.m3u.material.ktx.isTvDevice
import com.m3u.ui.Destination
import com.m3u.ui.ResumeEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

const val ROOT_ROUTE = "root_route"

fun NavController.popupToRoot() {
    this.popBackStack(ROOT_ROUTE, false)
}

fun NavGraphBuilder.rootGraph(
    pagerState: PagerState,
    roots: ImmutableList<Destination.Root>,
    contentPadding: PaddingValues,
    navigateToPlaylist: (Playlist) -> Unit,
    navigateToStream: () -> Unit,
    navigateToConsole: () -> Unit,
    navigateToAbout: () -> Unit,
    navigateToRecommendPlaylist: (Playlist, String) -> Unit,
    navigateToSettingPlaylistManagement: () -> Unit,
) {
    composable(ROOT_ROUTE) {
        RootGraph(
            pagerState = pagerState,
            roots = roots,
            contentPadding = contentPadding,
            navigateToPlaylist = navigateToPlaylist,
            navigateToStream = navigateToStream,
            navigateToConsole = navigateToConsole,
            navigateToAbout = navigateToAbout,
            navigateToRecommendPlaylist = navigateToRecommendPlaylist,
            navigateToSettingPlaylistManagement = navigateToSettingPlaylistManagement
        )
    }
}

@Composable
private fun RootGraph(
    pagerState: PagerState,
    roots: ImmutableList<Destination.Root>,
    contentPadding: PaddingValues,
    navigateToPlaylist: (Playlist) -> Unit,
    navigateToStream: () -> Unit,
    navigateToConsole: () -> Unit,
    navigateToAbout: () -> Unit,
    navigateToRecommendPlaylist: (Playlist, String) -> Unit,
    navigateToSettingPlaylistManagement: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(pagerState) {
        snapshotFlow {
            with(pagerState) {
                PagerStateSnapshot(
                    currentPage,
                    targetPage,
                    settledPage,
                    isScrollInProgress
                )
            }
        }
            .onEach {
                Log.d("PagerState", it.toString())
            }
            .launchIn(this)
    }

    HorizontalPager(
        state = pagerState,
        userScrollEnabled = !isTvDevice(),
        modifier = modifier
            .fillMaxSize()
            .blurEdge(
                edge = Edge.Bottom,
                color = MaterialTheme.colorScheme.background
            )
    ) { pagerIndex ->
        when (val root = roots[pagerIndex]) {
            Destination.Root.Foryou -> {
                ForyouRoute(
                    navigateToPlaylist = navigateToPlaylist,
                    navigateToStream = navigateToStream,
                    navigateToRecommendPlaylist = navigateToRecommendPlaylist,
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

            is Destination.Root.Setting -> {
                SettingRoute(
                    navigateToConsole = navigateToConsole,
                    navigateToAbout = navigateToAbout,
                    contentPadding = contentPadding,
                    targetFragment = root.targetFragment,
                    resume = rememberResumeEvent(pagerState.settledPage, pagerIndex),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private data class PagerStateSnapshot(
    val current: Int,
    val target: Int,
    val settled: Int,
    val scrolling: Boolean
)

@Composable
private fun rememberResumeEvent(currentPage: Int, targetPage: Int): ResumeEvent =
    remember(currentPage, targetPage) {
        if (currentPage == targetPage) eventOf(Unit)
        else handledEvent()
    }
