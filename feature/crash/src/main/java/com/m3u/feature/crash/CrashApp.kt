package com.m3u.feature.crash

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.m3u.feature.crash.navigation.Destination
import com.m3u.feature.crash.screen.detail.DetailScreen
import com.m3u.feature.crash.screen.list.ListScreen

@Composable
internal fun CrashApp() {
    Box(Modifier.systemBarsPadding()) {
        var destination: Destination by remember { mutableStateOf(Destination.List) }
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