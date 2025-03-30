package com.m3u.tv.screens

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector
import com.m3u.business.playlist.PlaylistNavigation
import com.m3u.i18n.R
import com.m3u.tv.screens.playlist.ChannelScreen
import com.m3u.tv.screens.player.VideoPlayerScreen

enum class Screens(
    private val args: List<String>? = null,
    val isTabItem: Boolean = false,
    val tabIcon: ImageVector? = null,
    @StringRes val title: Int? = null
) {
    Profile,
    Home(
        title = R.string.ui_destination_foryou,
        isTabItem = true
    ),
    Playlist(
        title = R.string.ui_destination_playlist,
        isTabItem = true,
        args = listOf(PlaylistNavigation.TYPE_URL)
    ),
    Favorite(
        title = R.string.ui_destination_favourite,
        isTabItem = true,
    ),
    Search(
        isTabItem = true,
        tabIcon = Icons.Default.Search
    ),
    Channel(args = listOf(ChannelScreen.ChannelIdBundleKey)),
    Dashboard,
    VideoPlayer(args = listOf(VideoPlayerScreen.ChannelIdBundleKey));

    operator fun invoke(): String {
        val argList = StringBuilder()
        args?.let { nnArgs ->
            nnArgs.forEach { arg -> argList.append("/{$arg}") }
        }
        return name + argList
    }

    fun withArgs(vararg args: Any): String {
        val destination = StringBuilder()
        args.forEach { arg ->
            val path = when (arg) {
                is String -> Uri.encode(arg)
                else -> arg
            }
            destination.append("/$path")
        }
        return name + destination
    }
}
