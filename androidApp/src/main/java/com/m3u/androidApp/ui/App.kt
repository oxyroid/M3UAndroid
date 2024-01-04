package com.m3u.androidApp.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.androidApp.navigation.M3UNavHost
import com.m3u.material.model.LocalNavController
import com.m3u.ui.EmptyHelper
import com.m3u.ui.Helper
import com.m3u.ui.useRailNav

@Composable
fun App(
    state: AppState = rememberAppState(),
    viewModel: AppViewModel = hiltViewModel(),
    helper: Helper = EmptyHelper,
) {
    val message by viewModel.message.collectAsStateWithLifecycle()
    val actions by viewModel.actions.collectAsStateWithLifecycle()
    val fob by viewModel.fob.collectAsStateWithLifecycle()

    val navDestination = state.navDestination
    val rootDestination = state.rootDestination

    val isBackPressedVisible = AppDefaults.isBackPressedVisible(navDestination)

    val title: String by AppDefaults.title(
        rootDestination = rootDestination,
        defState = viewModel.title.collectAsStateWithLifecycle()
    )

    M3UScaffold(
        title = title,
        message = message,
        useRailNav = helper.useRailNav,
        actions = actions,
        rootDestination = rootDestination,
        fob = fob,
        onBackPressed = state::onBackClick.takeIf { isBackPressedVisible },
        navigate = state::navigate,
        modifier = Modifier.fillMaxSize(),
    ) { contentPadding ->
        CompositionLocalProvider(
            LocalNavController provides state.navController,
        ) {
            M3UNavHost(
                pagerState = state.pagerState,
                navigate = state::navigate,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
