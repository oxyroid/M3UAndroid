package com.m3u.androidApp.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.ui.helper.LocalHelper

@Composable
fun App(
    modifier: Modifier = Modifier,
    state: AppState = rememberAppState(),
    viewModel: AppViewModel = hiltViewModel(),
) {
    val helper = LocalHelper.current

    val title: String by viewModel.title.collectAsStateWithLifecycle()
    val actions by viewModel.actions.collectAsStateWithLifecycle()
    val message by helper.message.collectAsStateWithLifecycle()
    val fob by viewModel.fob.collectAsStateWithLifecycle()
    val deep by viewModel.deep.collectAsStateWithLifecycle()

    val rootDestination = state.rootDestination

    AppRootGraph(
        title = title,
        message = message,
        actions = actions,
        currentRootDestination = rootDestination,
        rootDestinations = state.rootDestinations,
        fob = fob,
        onBackPressed = state::onBackClick.takeIf { deep > 0 },
        navigateToRoot = state::navigateToRoot,
        modifier = Modifier.fillMaxSize().then(modifier),
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
