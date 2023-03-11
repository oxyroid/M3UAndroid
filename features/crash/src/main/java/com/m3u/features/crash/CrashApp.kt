package com.m3u.features.crash

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.m3u.features.crash.navigation.Destination
import com.m3u.features.crash.screen.detail.DetailScreen
import com.m3u.features.crash.screen.list.ListScreen

@Composable
internal fun CrashApp() {
    Box {
        var destination: Destination by remember {
            mutableStateOf(Destination.List)
        }
        ListScreen(
            navigateToDetail = { path ->
                destination = Destination.Detail(path)
            },
            modifier = Modifier.fillMaxSize()
        )

        if (destination is Destination.Detail) {
            DetailScreen(
                path = (destination as Destination.Detail).path,
                modifier = Modifier.fillMaxSize()
            )
        }
        BackHandler(destination != Destination.List) {
            destination = Destination.List
        }
    }
}