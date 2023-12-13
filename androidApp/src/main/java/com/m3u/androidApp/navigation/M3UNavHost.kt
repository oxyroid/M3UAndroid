package com.m3u.androidApp.navigation

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
import androidx.navigation.compose.NavHost
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.features.about.navigation.aboutScreen
import com.m3u.features.console.navigation.consoleScreen
import com.m3u.features.playlist.navigation.playlistScreen
import com.m3u.features.stream.PlayerActivity
import com.m3u.i18n.R.string
import com.m3u.material.model.LocalNavController
import com.m3u.ui.Destination
import com.m3u.ui.LocalHelper
import com.m3u.ui.Navigate

@Composable
fun M3UNavHost(
    pagerState: PagerState,
    navigate: Navigate,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    startDestination: String = ROOT_ROUTE
) {
    val helper = LocalHelper.current
    val context = LocalContext.current
    val pref = LocalPref.current
    val navController = LocalNavController.current
    NavHost(
        navController = navController,
        startDestination = startDestination,
        exitTransition = { slideOutVertically { -it / 5 } + fadeOut() },
        popEnterTransition = { slideInVertically { -it / 5 } + fadeIn() },
        modifier = modifier
    ) {
        rootGraph(
            pagerState = pagerState,
            contentPadding = contentPadding,
            navigateToPlaylist = { playlist ->
                helper.title = playlist.title.ifEmpty {
                    if (playlist.local) context.getString(string.feat_main_imported_playlist_title)
                    else ""
                }
                navigate(Destination.Playlist(playlist.url))
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
                navigate(Destination.Console)
            },
            navigateToAbout = {
                navigate(Destination.About)
            },
            navigateToSettingSubscription = {
                navigate(Destination.Root.Setting)
            }
        )

        playlistScreen(
            contentPadding = contentPadding,
            navigateToStream = {
                if (pref.zappingMode && PlayerActivity.isInPipMode) return@playlistScreen
                val options = ActivityOptions.makeCustomAnimation(
                    context,
                    0,
                    0
                )
                context.startActivity(
                    Intent(context, PlayerActivity::class.java),
                    options.toBundle()
                )
            }
        )
        consoleScreen()
        aboutScreen(contentPadding)
    }
}
