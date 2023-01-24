package com.m3u.features.live.navigation

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import com.google.accompanist.navigation.animation.composable
import com.m3u.data.entity.Live
import com.m3u.features.live.LiveRoute

const val liveNavigationRoute = "live_route"

fun NavController.navigateToLive(navOptions: NavOptions? = null) {
    this.navigate(liveNavigationRoute, navOptions)
}

@OptIn(ExperimentalAnimationApi::class)
fun NavGraphBuilder.liveScreen() {
    composable(liveNavigationRoute) {
        LiveRoute(
            live = Live(
                url = "rtsp://wowzaec2demo.streamlock.net/vod/mp4:BigBuckBunny_115k.mp4",
                label = "Test"
            )
        )
    }
}
