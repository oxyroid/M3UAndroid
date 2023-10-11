package com.m3u.androidApp.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.androidApp.navigation.M3UNavHost
import com.m3u.ui.ktx.EventHandler
import com.m3u.ui.model.EmptyHelper
import com.m3u.ui.model.Helper

@Composable
fun App(
    appState: AppState = rememberAppState(),
    viewModel: AppViewModel = hiltViewModel(),
    helper: Helper = EmptyHelper
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snacker by viewModel.snacker.collectAsStateWithLifecycle()
    val actions by viewModel.actions.collectAsStateWithLifecycle()
    val fob by viewModel.fob.collectAsStateWithLifecycle()

    val navDestination = appState.navDestination
    val rootDestination = appState.rootDestination

    val isSystemBarVisible = AppDefaults.isSystemBarVisible(navDestination)
    val isBackPressedVisible = AppDefaults.isBackPressedVisible(navDestination)
    val isSystemBarScrollable = AppDefaults.isSystemBarScrollable(navDestination)
    val isPlaying = AppDefaults.isPlaying(navDestination)

    val cinemaMode = state.cinemaMode

    val theme = AppDefaults.theme(state.cinemaMode)
    val title: String by AppDefaults.title(
        rootDestination = rootDestination,
        destination = viewModel.title.collectAsStateWithLifecycle()
    )

    AppScaffold(
        title = title,
        snacker = snacker,
        actions = actions,
        rootDestination = rootDestination,
        fob = fob,
        isSystemBarVisible = isSystemBarVisible,
        isSystemBarScrollable = isSystemBarScrollable,
        onBackPressed = appState::onBackClick.takeIf { isBackPressedVisible },
        navigateTo = appState::navigateTo,
        modifier = Modifier.fillMaxSize(),
        theme = theme,
        helper = helper,
        cinemaMode = cinemaMode,
        isPlaying = isPlaying
    ) {
        M3UNavHost(
            navController = appState.navController,
            currentPage = appState.currentPage,
            onCurrentPage = { appState.currentPage = it },
            navigateTo = appState::navigateTo,
            modifier = Modifier.fillMaxSize()
        )
    }

    EventHandler(state.rootDestination) {
        appState.navigateTo(it)
    }
}
