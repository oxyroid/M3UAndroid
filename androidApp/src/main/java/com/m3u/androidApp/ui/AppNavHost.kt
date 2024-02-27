package com.m3u.androidApp.ui

import android.app.ActivityOptions
import android.content.Intent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.core.wrapper.eventOf
import com.m3u.features.about.navigation.aboutScreen
import com.m3u.features.about.navigation.navigateToAbout
import com.m3u.features.playlist.navigation.navigateToPlaylist
import com.m3u.features.playlist.navigation.playlistScreen
import com.m3u.features.playlist.navigation.playlistTvScreen
import com.m3u.features.stream.PlayerActivity
import com.m3u.material.ktx.isTelevision
import com.m3u.ui.Destination
import com.m3u.ui.Param
import com.m3u.ui.EventBus
import com.m3u.ui.LocalNavController

@Composable
fun AppNavHost(
    currentDestination: () -> Destination.Root,
    navigateToRoot: (Destination.Root) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    startDestination: String = ROOT_ROUTE
) {
    val context = LocalContext.current
    val pref = LocalPref.current
    val navController = LocalNavController.current

    val tv = isTelevision()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        exitTransition = { slideOutVertically { -it / 5 } + fadeOut() },
        popEnterTransition = { slideInVertically { -it / 5 } + fadeIn() },
        modifier = modifier
    ) {
        playlistScreen(
            navigateToStream = {
                if (pref.zappingMode && PlayerActivity.isInPipMode) return@playlistScreen
                val options = ActivityOptions.makeCustomAnimation(
                    context,
                    0,
                    0
                )
                context.startActivity(
                    Intent(context, PlayerActivity::class.java).apply {
                        // addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                    options.toBundle()
                )
            },
            contentPadding = contentPadding
        )
        rootGraph(
            currentDestination = currentDestination,
            contentPadding = contentPadding,
            navigateToPlaylist = { playlist ->
                navController.navigateToPlaylist(playlist.url, tv, playlist.type)
            },
            navigateToStream = {
                if (pref.zappingMode && PlayerActivity.isInPipMode) return@rootGraph
                val options = ActivityOptions.makeCustomAnimation(
                    context,
                    0,
                    0
                )
                context.startActivity(
                    Intent(context, PlayerActivity::class.java),
                    options.toBundle()
                )
            },
            navigateToAbout = {
                navController.navigateToAbout()
            },
            navigateToSettingPlaylistManagement = {
                navigateToRoot(Destination.Root.Setting)
                EventBus.setting = eventOf(Param.Setting.Playlists)
            }
        )

        playlistTvScreen()
        aboutScreen(contentPadding)
    }
}
