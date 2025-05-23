package com.m3u.smartphone.ui.common

import android.app.ActivityOptions
import android.content.Intent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.m3u.business.playlist.configuration.navigateToPlaylistConfiguration
import com.m3u.business.playlist.navigateToPlaylist
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.preferenceOf
import com.m3u.core.wrapper.eventOf
import com.m3u.smartphone.ui.business.channel.PlayerActivity
import com.m3u.smartphone.ui.business.configuration.playlistConfigurationScreen
import com.m3u.smartphone.ui.business.playlist.playlistScreen
import com.m3u.smartphone.ui.common.internal.Events
import com.m3u.smartphone.ui.material.components.Destination
import com.m3u.smartphone.ui.material.components.SettingDestination

@Composable
fun AppNavHost(
    navController: NavHostController,
    navigateToRootDestination: (Destination.Root) -> Unit,
    navigateToChannel: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    startDestination: String = Destination.Root.Foryou.name
) {
    val context = LocalContext.current

    val zappingMode by preferenceOf(PreferencesKeys.ZAPPING_MODE)

    NavHost(
        navController = navController,
        startDestination = startDestination,
        exitTransition = { slideOutVertically { -it / 5 } + fadeOut() },
        popEnterTransition = { slideInVertically { -it / 5 } + fadeIn() },
        modifier = modifier
    ) {
        rootGraph(
            contentPadding = contentPadding,
            navigateToPlaylist = { playlist ->
                navController.navigateToPlaylist(playlist.url)
            },
            navigateToChannel = navigateToChannel,
            navigateToSettingPlaylistManagement = {
                navigateToRootDestination(Destination.Root.Setting)
                Events.settingDestination = eventOf(SettingDestination.Playlists)
            },
            navigateToPlaylistConfiguration = {
                navController.navigateToPlaylistConfiguration(it.url)
            }
        )
        playlistScreen(
            navigateToChannel = {
                if (zappingMode && PlayerActivity.isInPipMode) return@playlistScreen
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
        playlistConfigurationScreen(contentPadding)
    }
}
