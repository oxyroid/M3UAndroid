package com.m3u.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.m3u.tv.screens.Screens
import com.m3u.tv.screens.dashboard.DashboardScreen
import com.m3u.tv.screens.movies.ChannelScreen
import com.m3u.tv.screens.videoPlayer.VideoPlayerScreen

@Composable
fun App(
    onBackPressed: () -> Unit
) {

    val navController = rememberNavController()
    var isComingBackFromDifferentScreen by remember { mutableStateOf(false) }

    NavHost(
        navController = navController,
        startDestination = Screens.Dashboard(),
        builder = {
            composable(
                route = Screens.Channel(),
                arguments = listOf(
                    navArgument(ChannelScreen.ChannelIdBundleKey) {
                        type = NavType.StringType
                    }
                )
            ) {
                ChannelScreen(
                    goToChannelPlayer = {
                        navController.navigate(Screens.VideoPlayer())
                    },
                    refreshScreenWithNewChannel = { channel ->
                        navController.navigate(
                            Screens.Channel.withArgs(channel.id)
                        ) {
                            popUpTo(Screens.Channel()) {
                                inclusive = true
                            }
                        }
                    },
                    onBackPressed = {
                        if (navController.navigateUp()) {
                            isComingBackFromDifferentScreen = true
                        }
                    }
                )
            }
            composable(route = Screens.Dashboard()) {
                DashboardScreen(
                    openChannelScreen = { channelId ->
                        navController.navigate(
                            Screens.Channel.withArgs(channelId)
                        )
                    },
                    openVideoPlayer = {
                        navController.navigate(Screens.VideoPlayer())
                    },
                    onBackPressed = onBackPressed,
                    isComingBackFromDifferentScreen = isComingBackFromDifferentScreen,
                    resetIsComingBackFromDifferentScreen = {
                        isComingBackFromDifferentScreen = false
                    }
                )
            }
            composable(route = Screens.VideoPlayer()) {
                VideoPlayerScreen(
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
