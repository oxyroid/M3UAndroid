package com.m3u.tv.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector
import com.m3u.tv.screens.movies.ChannelScreen
import com.m3u.tv.screens.videoPlayer.VideoPlayerScreen

enum class Screens(
    private val args: List<String>? = null,
    val isTabItem: Boolean = false,
    val tabIcon: ImageVector? = null
) {
    Profile,
    Home(isTabItem = true),
    Channels(isTabItem = true),
    Shows(isTabItem = true),
    Search(isTabItem = true, tabIcon = Icons.Default.Search),
    Channel(listOf(ChannelScreen.ChannelIdBundleKey)),
    Dashboard,
    VideoPlayer(listOf(VideoPlayerScreen.ChannelIdBundleKey));

    operator fun invoke(): String {
        val argList = StringBuilder()
        args?.let { nnArgs ->
            nnArgs.forEach { arg -> argList.append("/{$arg}") }
        }
        return name + argList
    }

    fun withArgs(vararg args: Any): String {
        val destination = StringBuilder()
        args.forEach { arg -> destination.append("/$arg") }
        return name + destination
    }
}
