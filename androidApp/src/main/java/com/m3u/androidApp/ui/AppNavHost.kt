package com.m3u.androidApp.ui

import android.app.ActivityOptions
import android.content.Intent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.features.about.navigation.aboutScreen
import com.m3u.features.about.navigation.navigateToAbout
import com.m3u.features.console.navigation.consoleScreen
import com.m3u.features.console.navigation.navigateToConsole
import com.m3u.features.playlist.navigation.navigateToPlaylist
import com.m3u.features.playlist.navigation.playlistScreen
import com.m3u.features.playlist.navigation.playlistTvScreen
import com.m3u.features.stream.PlayerActivity
import com.m3u.material.ktx.isTelevision
import com.m3u.ui.Destination
import com.m3u.ui.Destination.Root.Setting.SettingFragment
import kotlinx.collections.immutable.ImmutableList

@Composable
fun AppNavHost(
    pagerState: PagerState,
    roots: ImmutableList<Destination.Root>,
    navigateToRoot: (Destination.Root) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = ROOT_ROUTE
) {
    val context = LocalContext.current
    val pref = LocalPref.current

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
            pagerState = pagerState,
            roots = roots,
            contentPadding = contentPadding,
            navigateToPlaylist = { playlist ->
                navController.navigateToPlaylist(playlist.url, null, tv)
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
            navigateToConsole = {
                navController.navigateToConsole()
            },
            navigateToAbout = {
                navController.navigateToAbout()
            },
            navigateToRecommendPlaylist = { playlist, recommend ->
                navController.navigateToPlaylist(playlist.url, recommend, tv)
            },
            navigateToSettingPlaylistManagement = {
                navigateToRoot(Destination.Root.Setting(SettingFragment.Playlists))
            }
        )

        playlistTvScreen()
        consoleScreen(contentPadding)
        aboutScreen(contentPadding)
    }
}
