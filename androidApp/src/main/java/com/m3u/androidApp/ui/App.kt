package com.m3u.androidApp.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.ui.LocalHelper

@Composable
fun App(
    state: AppState = rememberAppState(),
    viewModel: AppViewModel = hiltViewModel(),
) {
    val helper = LocalHelper.current

    val actions by viewModel.actions.collectAsStateWithLifecycle()
    val message by helper.message.collectAsStateWithLifecycle()
    val fob by viewModel.fob.collectAsStateWithLifecycle()

    val navDestination = state.navDestination
    val rootDestination = state.rootDestination

    val isBackPressedVisible = AppDefaults.isBackPressedVisible(navDestination)

    val title: String by viewModel.title.collectAsStateWithLifecycle()

    AppRootGraph(
        title = title,
        message = message,
        actions = actions,
        currentRootDestination = rootDestination,
        rootDestinations = state.rootDestinations,
        fob = fob,
        onBackPressed = state::onBackClick.takeIf { isBackPressedVisible },
        navigateToRoot = state::navigateToRoot,
        modifier = Modifier.fillMaxSize(),
    ) { contentPadding ->
        AppNavHost(
            pagerState = state.pagerState,
            roots = state.rootDestinations,
            navigateToRoot = state::navigateToRoot,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
            navController = state.navController
        )
    }
}
