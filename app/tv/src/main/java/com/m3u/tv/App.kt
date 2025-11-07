package com.m3u.tv

import androidx.compose.animation.fadeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.m3u.business.playlist.PlaylistNavigation
import com.m3u.data.service.MediaCommand
import com.m3u.tv.screens.Screens
import com.m3u.tv.screens.dashboard.DashboardScreen
import com.m3u.tv.screens.player.ChannelScreen
import com.m3u.tv.screens.playlist.ChannelDetailScreen
import com.m3u.tv.screens.playlist.PlaylistScreen
import com.m3u.tv.utils.LocalHelper
import kotlinx.coroutines.launch

@Composable
fun App(
    onBackPressed: () -> Unit,
    onRouteChange: (String) -> Unit = {}
) {
    val helper = LocalHelper.current
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    var isComingBackFromDifferentScreen by remember { mutableStateOf(false) }

    // Track current route and notify MainActivity
    val currentRoute by navController.currentBackStackEntryFlow
        .collectAsState(initial = navController.currentBackStackEntry)

    androidx.compose.runtime.LaunchedEffect(currentRoute) {
        currentRoute?.destination?.route?.let { route ->
            onRouteChange(route)
        }
    }
    val navigateToChannel: (Int) -> Unit = { channelId: Int ->
        coroutineScope.launch {
            helper.play(MediaCommand.Common(channelId))
            navController.navigate(Screens.Channel())
        }
    }
    val navigateToChannelDetail: (Int) -> Unit = { channelId: Int ->
        navController.navigate(
            Screens.ChannelDetail.withArgs(channelId)
        )
    }
    NavHost(
        navController = navController,
        startDestination = Screens.Dashboard(),
        builder = {
            composable(
                route = Screens.Dashboard(),
                enterTransition = { null },
                exitTransition = { null }
            ) {
                DashboardScreen(
                    navigateToPlaylist = { playlistUrl ->
                        coroutineScope.launch {
                            navController.navigate(
                                Screens.Playlist.withArgs(playlistUrl)
                            )
                        }
                    },
                    navigateToChannel = navigateToChannel,
                    navigateToChannelDetail = navigateToChannelDetail,
                    onBackPressed = onBackPressed,
                    isComingBackFromDifferentScreen = isComingBackFromDifferentScreen,
                    resetIsComingBackFromDifferentScreen = {
                        isComingBackFromDifferentScreen = false
                    }
                )
            }

            composable(
                route = Screens.Playlist(),
                arguments = listOf(
                    navArgument(PlaylistNavigation.TYPE_URL) {
                        type = NavType.StringType
                    }
                ),
                enterTransition = { fadeIn() }
            ) {
                PlaylistScreen(
                    onChannelClick = { channel -> navigateToChannel(channel.id) }
                )
            }
            composable(
                route = Screens.Channel()
            ) {
                ChannelScreen(
                    onBackPressed = {
                        if (navController.navigateUp()) {
                            isComingBackFromDifferentScreen = true
                        }
                    }
                )
            }
            composable(
                route = Screens.ChannelDetail(),
                arguments = listOf(
                    navArgument(ChannelDetailScreen.ChannelIdBundleKey) {
                        type = NavType.IntType
                    }
                )
            ) {
                ChannelDetailScreen(
                    navigateToChannel = {
                        navController.navigate(Screens.Channel())
                    },
                    onBackPressed = {
                        if (navController.navigateUp()) {
                            isComingBackFromDifferentScreen = true
                        }
                    }
                )
            }
        }
    )
}
