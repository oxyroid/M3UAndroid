package com.m3u.features.crash

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import com.m3u.features.crash.navigation.Destination
import com.m3u.features.crash.screen.detail.DetailScreen
import com.m3u.features.crash.screen.list.ListScreen
import com.m3u.ui.shared.material.SharedSurface
import com.m3u.ui.shared.sharedState

@Composable
internal fun CrashApp() {
    val configuration = LocalConfiguration.current
    val scope = configuration.sharedState
    var destination by remember {
        mutableStateOf<Destination>(
            Destination.List
        )
    }
    SharedSurface(
        scope = destination,
        backgroundContent = {
            ListScreen(
                navigateToDetail = { path ->
                    destination = Destination.Detail(this, path)
                }
            )
        },
        foregroundContent = { modifier ->
            val target = this as Destination.Detail
            DetailScreen(
                path = target.path,
                modifier = modifier
            )
        },
        onStart = {
            destination = destination.copy(scope)
        }
    )

    BackHandler(destination != Destination.List) {
        destination = Destination.List
    }
}