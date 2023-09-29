package com.m3u.androidApp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.androidApp.components.BottomNavigationSheet
import com.m3u.androidApp.components.OptimizeBanner
import com.m3u.androidApp.components.PostDialog
import com.m3u.androidApp.components.PostDialogStatus
import com.m3u.androidApp.navigation.M3UNavHost
import com.m3u.core.util.withEach
import com.m3u.data.database.entity.Post
import com.m3u.ui.M3ULocalProvider
import com.m3u.ui.components.AppTopBar
import com.m3u.ui.components.IconButton
import com.m3u.ui.ktx.EventHandler
import com.m3u.ui.model.ABlackTheme
import com.m3u.ui.model.DayTheme
import com.m3u.ui.model.Helper
import com.m3u.ui.model.NightTheme
import com.m3u.ui.model.ScaffoldAction
import com.m3u.ui.model.ScaffoldFob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

typealias HelperConnector = (
    setTitle: (String) -> Unit,
    getTitle: () -> String,
    setActions: (List<ScaffoldAction>) -> Unit,
    getActions: () -> List<ScaffoldAction>,
    setFab: (ScaffoldFob?) -> Unit,
    getFab: () -> ScaffoldFob?
) -> Helper

@Composable
fun App(
    appState: AppState = rememberAppState(),
    viewModel: RootViewModel = hiltViewModel(),
    connector: HelperConnector = AppDefaults.EmptyHelperConnector
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val posts by viewModel.posts.collectAsStateWithLifecycle()
    val actions by viewModel.actions.collectAsStateWithLifecycle()
    val fob by viewModel.fob.collectAsStateWithLifecycle()
    val childTitle by viewModel.childTitle.collectAsStateWithLifecycle()

    val topLevelDestinations = appState.topLevelDestinations
    val currentDestination = appState.currentComposableNavDestination
    val currentTopLevelDestination = appState.currentTopLevelDestination

    val helper = connector(
        { viewModel.childTitle.value = it },
        viewModel.childTitle::value,
        { viewModel.actions.value = it },
        viewModel.actions::value,
        { viewModel.fob.value = it },
        viewModel.fob::value
    )

    val isSystemBarVisible = AppDefaults.isSystemBarVisible(currentDestination)
    val isBackPressedVisible = AppDefaults.isBackPressedVisible(currentDestination)
    val isSystemBarScrollable = AppDefaults.isSystemBarScrollable(currentDestination)
    val isPlaying = AppDefaults.isPlaying(currentDestination)

    val cinemaMode = state.cinemaMode
    val theme = when {
        cinemaMode -> ABlackTheme
        isSystemInDarkTheme() -> NightTheme
        else -> DayTheme
    }

    val post = state.post
    val onPost: (Post?) -> Unit = { viewModel.onEvent(RootEvent.OnPost(it)) }
    val parentTitle = currentTopLevelDestination
        ?.titleTextId
        ?.let { stringResource(it) }

    val title: String by remember(parentTitle) {
        derivedStateOf {
            parentTitle ?: childTitle
        }
    }
    var dialogStatus = remember(post, posts) {
        if (post == null || post.temporal) PostDialogStatus.Idle
        else {
            val index = posts.indexOf(post)
            val total = posts.size
            if (index != -1 && total > 0) {
                PostDialogStatus.Visible(
                    post = post,
                    index = index,
                    total = total
                )
            } else {
                onPost(null)
                PostDialogStatus.Idle
            }
        }
    }

    M3ULocalProvider(theme, helper) {
        val scope = rememberCoroutineScope()
        val useDarkIcons = when {
            cinemaMode -> false
            isPlaying -> false
            else -> !isSystemInDarkTheme()
        }
        DisposableEffect(
            useDarkIcons,
            scope,
            isPlaying,
            cinemaMode
        ) {
            scope.launch {
                if (!cinemaMode && isPlaying) {
                    delay(800.milliseconds)
                }
                helper.detectDarkMode { useDarkIcons }
            }
            onDispose {}
        }
        AppTopBar(
            title = title,
            visible = isSystemBarVisible,
            scrollable = isSystemBarScrollable,
            actions = {
                actions.withEach {
                    IconButton(
                        icon = icon,
                        contentDescription = contentDescription,
                        onClick = onClick
                    )
                }
            },
            onBackPressed = appState::onBackClick.takeIf { isBackPressedVisible }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                M3UNavHost(
                    navController = appState.navController,
                    currentPage = appState.currentPage,
                    onCurrentPage = { appState.currentPage = it },
                    destinations = topLevelDestinations,
                    navigateToDestination = appState::navigateToDestination,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                AnimatedVisibility(isSystemBarVisible) {
                    Column {
                        OptimizeBanner(
                            post = posts.firstOrNull(),
                            onPost = onPost,
                            modifier = Modifier.fillMaxWidth()
                        )

                        BottomNavigationSheet(
                            fob = fob,
                            destinations = topLevelDestinations,
                            destination = currentTopLevelDestination,
                            navigateToTopLevelDestination = {
                                appState.navigateToTopLevelDestination(it)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        PostDialog(
            status = dialogStatus,
            onDismiss = { onPost(null) },
            onNext = { viewModel.onEvent(RootEvent.OnNext) },
            onPrevious = { viewModel.onEvent(RootEvent.OnPrevious) },
            onRead = { viewModel.onEvent(RootEvent.OnRead) }
        )

        EventHandler(state.navigateTopLevelDestination) {
            appState.navigateToTopLevelDestination(it)
        }

        BackHandler(dialogStatus != PostDialogStatus.Idle) {
            dialogStatus = PostDialogStatus.Idle
        }
    }
}
