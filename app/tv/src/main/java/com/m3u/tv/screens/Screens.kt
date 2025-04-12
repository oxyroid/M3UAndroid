package com.m3u.tv.screens

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import com.m3u.business.playlist.PlaylistNavigation
import com.m3u.data.database.model.Playlist
import com.m3u.i18n.R
import com.m3u.tv.screens.playlist.ChannelDetailScreen

sealed class DashboardKey {
    data class Common(
        override val screens: Screens,
        override val focusRequester: FocusRequester
    ) : DashboardKey()

    data class PlaylistSpec(
        val playlist: Playlist,
        override val focusRequester: FocusRequester
    ) : DashboardKey() {
        override val screens: Screens = Screens.Playlist
    }

    data class ProfileSpec(
        override val focusRequester: FocusRequester
    ): DashboardKey() {
        override val screens: Screens = Screens.Profile
    }

    abstract val focusRequester: FocusRequester
    abstract val screens: Screens
}

enum class Screens(
    private val args: List<String>? = null,
    val isTabItem: Boolean = false,
    val tabIcon: ImageVector? = null,
    @StringRes val title: Int? = null
) {
    Profile(isTabItem = true),
    Home(
        title = R.string.ui_destination_foryou,
        isTabItem = true
    ),
    Playlist(
        title = R.string.ui_destination_playlist,
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
    Channel,
    ChannelDetail(args = listOf(ChannelDetailScreen.ChannelIdBundleKey)),
    Dashboard;

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
